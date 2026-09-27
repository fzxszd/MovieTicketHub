"""Authoritative, account-scoped favorite state and incremental sync protocol."""
from __future__ import annotations

import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from typing import Any


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def initialize_favorite_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE IF NOT EXISTS favorite_states(
          user_id TEXT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
          movie_id INTEGER NOT NULL CHECK(movie_id>0),
          is_favorite INTEGER NOT NULL CHECK(is_favorite IN (0,1)),
          server_revision INTEGER NOT NULL CHECK(server_revision>=1),
          accepted_at TEXT NOT NULL,
          last_operation_id TEXT NOT NULL,
          PRIMARY KEY(user_id,movie_id));
        CREATE INDEX IF NOT EXISTS idx_favorite_states_revision
          ON favorite_states(user_id,server_revision);
        CREATE TABLE IF NOT EXISTS favorite_operations(
          user_id TEXT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
          client_operation_id TEXT NOT NULL,
          movie_id INTEGER NOT NULL CHECK(movie_id>0),
          target_state INTEGER NOT NULL CHECK(target_state IN (0,1)),
          device_id_hash TEXT NOT NULL,
          local_sequence INTEGER NOT NULL CHECK(local_sequence>0),
          server_revision INTEGER NOT NULL CHECK(server_revision>=0),
          accepted_at TEXT NOT NULL,
          result TEXT NOT NULL CHECK(result IN ('ACCEPTED','ALREADY_PROCESSED','REJECTED')),
          error_code TEXT,
          PRIMARY KEY(user_id,client_operation_id));
        CREATE INDEX IF NOT EXISTS idx_favorite_ops_revision
          ON favorite_operations(user_id,server_revision);
        INSERT INTO schema_metadata(key,value) VALUES ('favorite_revision','0')
          ON CONFLICT(key) DO NOTHING;
        """
    )


def _next_revision(connection: sqlite3.Connection) -> int:
    row = connection.execute("SELECT value FROM schema_metadata WHERE key='favorite_revision'").fetchone()
    revision = int(row[0] if row else 0) + 1
    connection.execute(
        "INSERT INTO schema_metadata(key,value) VALUES('favorite_revision',?) "
        "ON CONFLICT(key) DO UPDATE SET value=excluded.value", (str(revision),)
    )
    return revision


def _device_hash(device_id: str) -> str:
    return hashlib.sha256(device_id.encode("utf-8")).hexdigest()


def _change(row: sqlite3.Row) -> dict[str, Any]:
    change = {
        "movieId": row["movie_id"], "isFavorite": bool(row["is_favorite"]),
        "serverRevision": int(row["server_revision"]), "acceptedAt": row["accepted_at"],
    }
    if row["movie_title"] is not None or row["poster_url"] is not None:
        change["movie"] = {
            "title": row["movie_title"], "posterUrl": row["poster_url"],
            "availability": row["movie_availability"] or "AVAILABLE",
        }
    return change


def current_revision(connection: sqlite3.Connection) -> int:
    row = connection.execute("SELECT value FROM schema_metadata WHERE key='favorite_revision'").fetchone()
    return int(row[0]) if row else 0


def list_changes(connection: sqlite3.Connection, user_id: str, after_revision: int, limit: int) -> dict[str, Any]:
    rows = connection.execute(
        """SELECT f.movie_id,f.target_state AS is_favorite,f.server_revision,f.accepted_at,
                  c.title AS movie_title,c.poster_url,c.availability AS movie_availability
           FROM favorite_operations f LEFT JOIN movie_catalog c ON c.movie_id=f.movie_id
           WHERE f.user_id=? AND f.server_revision>? AND f.result='ACCEPTED'
           ORDER BY f.server_revision LIMIT ?""",
        (user_id, after_revision, limit + 1),
    ).fetchall()
    page = rows[:limit]
    current = current_revision(connection)
    # A legacy client may already have an up-to-date revision cursor but still
    # lack the movie snapshot. Re-send current favorites with catalog metadata
    # so the local card can repair its poster/title without changing revision.
    if not page and after_revision >= current:
        page = connection.execute(
            """SELECT f.movie_id,f.is_favorite,f.server_revision,f.accepted_at,
                      c.title AS movie_title,c.poster_url,c.availability AS movie_availability
               FROM favorite_states f JOIN movie_catalog c ON c.movie_id=f.movie_id
               WHERE f.user_id=? AND f.is_favorite=1 AND c.poster_url IS NOT NULL
               ORDER BY f.movie_id LIMIT ?""",
            (user_id, limit),
        ).fetchall()
    next_revision = int(page[-1]["server_revision"]) if page else after_revision
    return {"serverRevision": current, "changes": [_change(r) for r in page],
            "hasMore": len(rows) > limit, "nextRevision": next_revision}


def process_operation(connection: sqlite3.Connection, user_id: str, operation: dict[str, Any],
                      device_id: str, *, require_sequence: bool = False) -> dict[str, Any]:
    op_id = operation.get("clientOperationId") or operation.get("idempotencyKey")
    movie_id, target, sequence = operation.get("movieId"), operation.get("targetState"), operation.get("localSequence")
    if not isinstance(op_id, str) or not 16 <= len(op_id) <= 128 or not isinstance(movie_id, int) or movie_id < 1 \
            or not isinstance(target, bool) or not isinstance(sequence, int) or sequence < 1:
        return {"clientOperationId": op_id or "", "movieId": movie_id or 0, "result": "REJECTED",
                "authoritativeState": False, "serverRevision": 0, "errorCode": "INVALID_OPERATION"}
    existing = connection.execute(
        "SELECT * FROM favorite_operations WHERE user_id=? AND client_operation_id=?", (user_id, op_id)
    ).fetchone()
    if existing:
        if int(existing["movie_id"]) != movie_id:
            return {"clientOperationId": op_id, "movieId": movie_id, "result": "REJECTED",
                    "authoritativeState": False, "serverRevision": 0, "errorCode": "OPERATION_CONFLICT"}
        state = connection.execute("SELECT is_favorite FROM favorite_states WHERE user_id=? AND movie_id=?", (user_id, movie_id)).fetchone()
        return {"clientOperationId": op_id, "movieId": movie_id, "result": "ALREADY_PROCESSED",
                "authoritativeState": bool(state[0]) if state else False,
                "serverRevision": int(existing["server_revision"]), "errorCode": existing["error_code"]}
    if require_sequence:
        last = connection.execute("SELECT MAX(local_sequence) FROM favorite_operations WHERE user_id=? AND device_id_hash=?", (user_id, _device_hash(device_id))).fetchone()[0]
        if last is not None and sequence <= int(last):
            return {"clientOperationId": op_id, "movieId": movie_id, "result": "REJECTED",
                    "authoritativeState": False, "serverRevision": 0, "errorCode": "INVALID_SEQUENCE"}
    revision = _next_revision(connection)
    accepted = now_iso()
    connection.execute(
        "INSERT INTO favorite_states(user_id,movie_id,is_favorite,server_revision,accepted_at,last_operation_id) VALUES(?,?,?,?,?,?) "
        "ON CONFLICT(user_id,movie_id) DO UPDATE SET is_favorite=excluded.is_favorite,server_revision=excluded.server_revision,accepted_at=excluded.accepted_at,last_operation_id=excluded.last_operation_id",
        (user_id, movie_id, int(target), revision, accepted, op_id),
    )
    connection.execute(
        "INSERT INTO favorite_operations VALUES(?,?,?,?,?,?,?,?,?,?)",
        (user_id, op_id, movie_id, int(target), _device_hash(device_id), sequence, revision, accepted, "ACCEPTED", None),
    )
    return {"clientOperationId": op_id, "movieId": movie_id, "result": "ACCEPTED",
            "authoritativeState": bool(target), "serverRevision": revision, "errorCode": None}


def migrate_legacy_favorites(connection: sqlite3.Connection) -> None:
    """Import only legacy favorites that can be mapped to an authenticated user once."""
    rows = connection.execute("SELECT id,email_normalized FROM user_accounts").fetchall()
    for user in rows:
        payload = connection.execute("SELECT payload FROM user_sync_data WHERE email=?", (user["email_normalized"],)).fetchone()
        if not payload:
            continue
        try:
            legacy_payload = json.loads(payload[0])
            if not isinstance(legacy_payload, dict):
                continue
            favorites = legacy_payload.get("favorites", [])
        except (TypeError, json.JSONDecodeError):
            continue
        for item in favorites if isinstance(favorites, list) else []:
            movie_id = item.get("id") if isinstance(item, dict) else None
            if not isinstance(movie_id, int) or movie_id < 1:
                continue
            exists = connection.execute("SELECT 1 FROM favorite_states WHERE user_id=? AND movie_id=?", (user["id"], movie_id)).fetchone()
            if exists:
                continue
            op_id = f"legacy-{user['id']}-{movie_id}"
            revision = _next_revision(connection)
            accepted = now_iso()
            connection.execute("INSERT INTO favorite_states VALUES(?,?,?,?,?,?)", (user["id"], movie_id, 1, revision, accepted, op_id))
            connection.execute("INSERT OR IGNORE INTO favorite_operations VALUES(?,?,?,?,?,?,?,?,?,?)", (user["id"], op_id, movie_id, 1, "legacy", revision, revision, accepted, "ACCEPTED", None))
        legacy_payload["favorites"] = []
        connection.execute("UPDATE user_sync_data SET payload=? WHERE email=?", (json.dumps(legacy_payload, ensure_ascii=False), user["email_normalized"]))
