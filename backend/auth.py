"""Authentication primitives shared by the Flask routes and migrations."""

from __future__ import annotations

import hashlib
import re
import secrets
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

from werkzeug.security import check_password_hash, generate_password_hash


EMAIL_PATTERN = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def to_timestamp(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat()


def parse_timestamp(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def normalize_email(value: Any) -> str:
    return value.strip().lower() if isinstance(value, str) else ""


def validate_email(value: Any) -> str | None:
    email = normalize_email(value)
    return email if email and len(email) <= 254 and EMAIL_PATTERN.fullmatch(email) else None


def normalize_username(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def validate_username(value: Any) -> str | None:
    username = normalize_username(value)
    if not 2 <= len(username) <= 30 or any(not char.isprintable() for char in username):
        return None
    return username


def validate_password(value: Any, *, migration: bool = False) -> str | None:
    password = value if isinstance(value, str) else ""
    minimum = 1 if migration else 8
    return password if minimum <= len(password) <= 64 else None


def hash_password(password: str) -> str:
    return generate_password_hash(password, method="scrypt")


def verify_password(password_hash: str, password: str) -> bool:
    try:
        return check_password_hash(password_hash, password)
    except (TypeError, ValueError):
        return False


def generate_token() -> str:
    return secrets.token_urlsafe(48)


def token_digest(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def create_session(connection: sqlite3.Connection, user_id: str, lifetime: timedelta,
                   now: datetime | None = None) -> tuple[str, str]:
    created = now or utc_now()
    expires = created + lifetime
    token = generate_token()
    connection.execute(
        "INSERT INTO auth_sessions(id,user_id,token_hash,created_at,expires_at,last_used_at) "
        "VALUES (?,?,?,?,?,?)",
        (str(uuid.uuid4()), user_id, token_digest(token), to_timestamp(created),
         to_timestamp(expires), to_timestamp(created)),
    )
    return token, to_timestamp(expires)


def parse_bearer_header(value: str | None) -> str | None:
    if not value:
        return None
    scheme, separator, token = value.partition(" ")
    return token.strip() if separator and scheme.lower() == "bearer" and token.strip() else None


def resolve_session(connection: sqlite3.Connection, authorization: str | None,
                    now: datetime | None = None) -> tuple[sqlite3.Row, sqlite3.Row] | None:
    token = parse_bearer_header(authorization)
    if token is None:
        return None
    session = connection.execute(
        "SELECT * FROM auth_sessions WHERE token_hash = ?", (token_digest(token),)
    ).fetchone()
    current = now or utc_now()
    if (session is None or session["revoked_at"] is not None
            or parse_timestamp(session["expires_at"]) <= current):
        return None
    user = connection.execute(
        "SELECT * FROM user_accounts WHERE id = ? AND status = 'active'", (session["user_id"],)
    ).fetchone()
    if user is None:
        return None
    connection.execute("UPDATE auth_sessions SET last_used_at = ? WHERE id = ?",
                       (to_timestamp(current), session["id"]))
    return user, session


def throttle_key(email: str, source: str) -> str:
    return hashlib.sha256(f"{normalize_email(email)}\0{source}".encode()).hexdigest()


def throttle_status(connection: sqlite3.Connection, email: str, source: str,
                    window: timedelta, now: datetime | None = None) -> tuple[str, sqlite3.Row | None, int]:
    current = now or utc_now()
    key = throttle_key(email, source)
    row = connection.execute("SELECT * FROM auth_throttle WHERE key = ?", (key,)).fetchone()
    if row is None:
        return key, None, 0
    blocked_until = parse_timestamp(row["blocked_until"]) if row["blocked_until"] else None
    if blocked_until and blocked_until > current:
        return key, row, max(1, int((blocked_until - current).total_seconds()) + 1)
    if parse_timestamp(row["window_started_at"]) + window <= current:
        connection.execute("DELETE FROM auth_throttle WHERE key = ?", (key,))
        return key, None, 0
    return key, row, 0


def record_login_failure(connection: sqlite3.Connection, email: str, source: str,
                         window: timedelta, limit: int, block_duration: timedelta,
                         now: datetime | None = None) -> int:
    current = now or utc_now()
    key, row, already_blocked = throttle_status(connection, email, source, window, current)
    if already_blocked:
        return already_blocked
    count = 1 if row is None else int(row["failure_count"]) + 1
    started = to_timestamp(current) if row is None else row["window_started_at"]
    blocked_until = current + block_duration if count >= limit else None
    connection.execute(
        "INSERT INTO auth_throttle(key,failure_count,window_started_at,blocked_until) VALUES (?,?,?,?) "
        "ON CONFLICT(key) DO UPDATE SET failure_count=excluded.failure_count," 
        "window_started_at=excluded.window_started_at,blocked_until=excluded.blocked_until",
        (key, count, started, to_timestamp(blocked_until) if blocked_until else None),
    )
    return max(1, int(block_duration.total_seconds())) if blocked_until else 0


def clear_login_failures(connection: sqlite3.Connection, email: str, source: str) -> None:
    connection.execute("DELETE FROM auth_throttle WHERE key = ?", (throttle_key(email, source),))
