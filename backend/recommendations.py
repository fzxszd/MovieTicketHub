"""Deterministic, account-scoped movie recommendations."""
from __future__ import annotations

import json
import sqlite3
import uuid
import math
from collections import Counter
from datetime import date, datetime, timezone
from typing import Any
from urllib.request import Request, urlopen

ALGORITHM_VERSION = "hybrid-v1"
CATALOG_VERSION = "catalog-v1"
MAOYAN_CATALOG_VERSION = "maoyan-list-v1"
AVAILABLE = "AVAILABLE"
MAOYAN_NOW_PLAYING_URL = "https://apis.netstart.cn/maoyan/index/movieOnInfoList"

SEED_CATALOG = [
    (1, "星际穿越", ["科幻", "剧情"], ["太空", "时间"], 98.0, "2024-01-01"),
    (2, "沙丘", ["科幻", "冒险"], ["太空", "家族"], 96.0, "2024-02-01"),
    (3, "银翼杀手", ["科幻", "悬疑"], ["人工智能", "未来"], 91.0, "2023-10-01"),
    (4, "海边的曼彻斯特", ["剧情"], ["家庭", "成长"], 85.0, "2023-05-01"),
    (5, "寻梦环游记", ["动画", "冒险"], ["家庭", "音乐"], 93.0, "2024-03-01"),
    (6, "蜘蛛侠", ["动作", "冒险"], ["英雄", "成长"], 94.0, "2024-04-01"),
    (7, "盗梦空间", ["科幻", "动作"], ["梦境", "时间"], 97.0, "2023-12-01"),
    (8, "小森林", ["剧情"], ["治愈", "生活"], 82.0, "2024-06-01"),
    (9, "头号玩家", ["科幻", "冒险"], ["游戏", "未来"], 89.0, "2024-07-01"),
    (10, "音乐之声", ["音乐", "剧情"], ["家庭", "成长"], 88.0, "2022-09-01"),
]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def initialize_recommendation_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE IF NOT EXISTS movie_catalog(
          movie_id INTEGER PRIMARY KEY CHECK(movie_id>0), title TEXT NOT NULL,
          genres_json TEXT NOT NULL, themes_json TEXT NOT NULL, language TEXT NOT NULL DEFAULT 'zh-CN',
          people_json TEXT NOT NULL DEFAULT '[]', popularity REAL NOT NULL CHECK(popularity>=0),
          release_date TEXT, availability TEXT NOT NULL CHECK(availability IN ('AVAILABLE','UNAVAILABLE')),
          catalog_version TEXT NOT NULL, updated_at TEXT NOT NULL, poster_url TEXT);
        CREATE TABLE IF NOT EXISTS recommendation_runs(
          id TEXT PRIMARY KEY, user_id TEXT REFERENCES user_accounts(id) ON DELETE CASCADE,
          source TEXT NOT NULL, algorithm_version TEXT NOT NULL, catalog_version TEXT NOT NULL,
          favorite_revision INTEGER, fallback_reason TEXT, created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS recommendation_items(
          run_id TEXT NOT NULL REFERENCES recommendation_runs(id) ON DELETE CASCADE,
          movie_id INTEGER NOT NULL REFERENCES movie_catalog(movie_id), rank INTEGER NOT NULL,
          score REAL NOT NULL, reason_type TEXT NOT NULL, reason_text TEXT NOT NULL,
          matched_features_json TEXT NOT NULL, PRIMARY KEY(run_id,movie_id));
        CREATE TABLE IF NOT EXISTS recommendation_metrics(
          event_id TEXT PRIMARY KEY, recommendation_id TEXT NOT NULL REFERENCES recommendation_runs(id) ON DELETE CASCADE,
          movie_id INTEGER NOT NULL, algorithm_version TEXT NOT NULL, source TEXT NOT NULL,
          rank INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('IMPRESSION','CLICK','FAVORITE','PURCHASE')),
          occurred_at TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS idx_recommendation_runs_user ON recommendation_runs(user_id,created_at);
        CREATE INDEX IF NOT EXISTS idx_recommendation_items_run ON recommendation_items(run_id,rank);
        """
    )
    columns = {row[1] for row in connection.execute("PRAGMA table_info(movie_catalog)")}
    if "poster_url" not in columns:
        connection.execute("ALTER TABLE movie_catalog ADD COLUMN poster_url TEXT")
    connection.execute("INSERT INTO schema_metadata(key,value) VALUES('catalog_version',?) ON CONFLICT(key) DO NOTHING", (CATALOG_VERSION,))


def seed_movie_catalog(connection: sqlite3.Connection) -> None:
    now = _now()
    for movie_id, title, genres, themes, popularity, release_date in SEED_CATALOG:
        connection.execute(
            """INSERT INTO movie_catalog(movie_id,title,genres_json,themes_json,language,people_json,popularity,release_date,availability,catalog_version,updated_at)
               VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(movie_id) DO UPDATE SET title=excluded.title,genres_json=excluded.genres_json,themes_json=excluded.themes_json,popularity=excluded.popularity,release_date=excluded.release_date,availability='AVAILABLE',catalog_version=excluded.catalog_version,updated_at=excluded.updated_at""",
            (movie_id, title, json.dumps(genres, ensure_ascii=False), json.dumps(themes, ensure_ascii=False), "zh-CN", "[]", popularity, release_date, AVAILABLE, CATALOG_VERSION, now),
        )


def sync_maoyan_catalog(connection: sqlite3.Connection, *, fetcher=None) -> bool:
    """Refresh recommendation candidates from the same Maoyan list used by Android.

    The catalog keeps the Maoyan movie ID, title, poster, genre and cast signals.
    Consequently a recommendation click opens that exact ID in Android's existing
    Maoyan detail API, so its title, synopsis and credits cannot drift apart.
    """
    try:
        if fetcher is None:
            request = Request(MAOYAN_NOW_PLAYING_URL, headers={"User-Agent": "MoviesApp-Compose/1.0"})
            with urlopen(request, timeout=5) as response:
                payload = json.load(response)
        else:
            payload = fetcher()
        movies = payload.get("movieList") if isinstance(payload, dict) else None
        if not isinstance(movies, list) or not movies:
            return False
    except Exception:
        return False

    now = _now()
    connection.execute("UPDATE movie_catalog SET availability='UNAVAILABLE' WHERE catalog_version=?", (MAOYAN_CATALOG_VERSION,))
    inserted = 0
    for movie in movies:
        if not isinstance(movie, dict) or not isinstance(movie.get("id"), int) or movie["id"] < 1:
            continue
        title = movie.get("nm")
        if not isinstance(title, str) or not title.strip():
            continue
        genres = [value.strip() for value in str(movie.get("cat") or "").replace("/", ",").split(",") if value.strip()]
        people = [value.strip() for value in str(movie.get("star") or "").split(",") if value.strip()]
        try:
            popularity = float(movie.get("wish") or 0) + float(movie.get("sc") or 0)
        except (TypeError, ValueError):
            popularity = 0.0
        connection.execute(
            """INSERT INTO movie_catalog(movie_id,title,genres_json,themes_json,language,people_json,popularity,release_date,availability,catalog_version,updated_at,poster_url)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(movie_id) DO UPDATE SET title=excluded.title,genres_json=excluded.genres_json,themes_json=excluded.themes_json,
                 people_json=excluded.people_json,popularity=excluded.popularity,release_date=excluded.release_date,availability=excluded.availability,
                 catalog_version=excluded.catalog_version,updated_at=excluded.updated_at,poster_url=excluded.poster_url""",
            (movie["id"], title.strip(), json.dumps(genres, ensure_ascii=False), json.dumps(people, ensure_ascii=False), "zh-CN",
             json.dumps(people, ensure_ascii=False), popularity, movie.get("rt"), AVAILABLE, MAOYAN_CATALOG_VERSION, now, movie.get("img")),
        )
        inserted += 1
    if inserted:
        connection.execute("INSERT INTO schema_metadata(key,value) VALUES('catalog_version',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (MAOYAN_CATALOG_VERSION,))
    return inserted > 0


def _catalog_rows(connection: sqlite3.Connection) -> list[sqlite3.Row]:
    return connection.execute("SELECT * FROM movie_catalog WHERE availability='AVAILABLE' ORDER BY popularity DESC,movie_id ASC").fetchall()


def _favorite_ids(connection: sqlite3.Connection, user_id: str | None) -> tuple[set[int], int]:
    if not user_id:
        return set(), 0
    rows = connection.execute("SELECT movie_id FROM favorite_states WHERE user_id=? AND is_favorite=1", (user_id,)).fetchall()
    revision_row = connection.execute("SELECT value FROM schema_metadata WHERE key='favorite_revision'").fetchone()
    return {int(row[0]) for row in rows}, int(revision_row[0]) if revision_row else 0


def _paid_ids(connection: sqlite3.Connection, user_id: str | None) -> set[int]:
    if not user_id:
        return set()
    rows = connection.execute(
        """SELECT DISTINCT s.movie_id FROM orders o JOIN seat_locks l ON l.id=o.seat_lock_id
           JOIN showtimes s ON s.id=l.showtime_id WHERE o.user_id=? AND o.status='PAID'""", (user_id,)
    ).fetchall()
    return {int(row[0]) for row in rows}


def _interaction_sets(connection: sqlite3.Connection) -> dict[str, set[int]]:
    """Build an account-scoped implicit-feedback matrix for item-item CF.

    Favorites are the strongest signal; paid orders and recommendation clicks
    add weaker but useful signals once multiple accounts have used the app.
    """
    interactions: dict[str, set[int]] = {}
    for row in connection.execute("SELECT user_id,movie_id FROM favorite_states WHERE is_favorite=1"):
        interactions.setdefault(str(row["user_id"]), set()).add(int(row["movie_id"]))
    for row in connection.execute(
        """SELECT o.user_id,s.movie_id FROM orders o
           JOIN seat_locks l ON l.id=o.seat_lock_id JOIN showtimes s ON s.id=l.showtime_id
           WHERE o.status='PAID'"""
    ):
        interactions.setdefault(str(row["user_id"]), set()).add(int(row["movie_id"]))
    for row in connection.execute(
        """SELECT r.user_id,m.movie_id FROM recommendation_metrics m
           JOIN recommendation_runs r ON r.id=m.recommendation_id
           WHERE r.user_id IS NOT NULL AND m.action IN ('CLICK','FAVORITE','PURCHASE')"""
    ):
        interactions.setdefault(str(row["user_id"]), set()).add(int(row["movie_id"]))
    return interactions


def _collaborative_scores(connection: sqlite3.Connection, user_id: str, favorites: set[int], candidates: list[sqlite3.Row]) -> dict[int, float]:
    """Item-item cosine-style similarity from other accounts' implicit feedback."""
    interactions = _interaction_sets(connection)
    item_users: dict[int, set[str]] = {}
    for account_id, movies in interactions.items():
        for movie_id in movies:
            item_users.setdefault(movie_id, set()).add(account_id)
    scores: dict[int, float] = {}
    for row in candidates:
        movie_id = int(row["movie_id"]); candidate_users = item_users.get(movie_id, set())
        if not candidate_users:
            continue
        similarity = 0.0
        for favorite_id in favorites:
            favorite_users = item_users.get(favorite_id, set())
            if favorite_users:
                similarity += len(favorite_users & candidate_users) / math.sqrt(len(favorite_users) * len(candidate_users))
        if similarity:
            scores[movie_id] = similarity
    return scores


def _freshness_score(value: str | None) -> float:
    if not value:
        return 0.0
    try:
        age_days = max(0, (date.today() - date.fromisoformat(value[:10])).days)
    except ValueError:
        return 0.0
    return max(0.0, 1.0 - age_days / 180.0)


def _parse(row: sqlite3.Row, key: str) -> list[str]:
    try:
        value = json.loads(row[key])
        return sorted({str(x) for x in value if isinstance(x, str)}) if isinstance(value, list) else []
    except (TypeError, json.JSONDecodeError):
        return []


def _fallback(connection: sqlite3.Connection, user_id: str | None, reason: str, limit: int, now_playing: bool = False) -> dict[str, Any]:
    rows = _catalog_rows(connection)[:limit]
    source = "FALLBACK_NOW_PLAYING" if now_playing else "FALLBACK_POPULAR"
    label = "正在上映" if now_playing else "热门推荐"
    run_id = str(uuid.uuid4())
    connection.execute("INSERT INTO recommendation_runs VALUES(?,?,?,?,?,?,?,?)", (run_id, user_id, source, ALGORITHM_VERSION, CATALOG_VERSION, None, reason, _now()))
    for rank, row in enumerate(rows, 1):
        typ = "NOW_PLAYING" if now_playing else "POPULAR"
        text = "正在上映的电影" if now_playing else "大家都在看的热门电影"
        connection.execute("INSERT INTO recommendation_items VALUES(?,?,?,?,?,?,?)", (run_id, row["movie_id"], rank, float(row["popularity"]), typ, text, "[]"))
    return _response(connection, run_id, source, label, "NOT_APPLICABLE", None, reason, rows)


def _response(connection: sqlite3.Connection, run_id: str, source: str, label: str, state: str, favorite_revision: int | None, fallback_reason: str | None, rows: list[sqlite3.Row]) -> dict[str, Any]:
    items = []
    for row in connection.execute("SELECT i.*,c.title,c.poster_url FROM recommendation_items i JOIN movie_catalog c ON c.movie_id=i.movie_id WHERE i.run_id=? ORDER BY i.rank", (run_id,)).fetchall():
        items.append({"movieId": int(row["movie_id"]), "title": row["title"], "posterUrl": row["poster_url"], "availability": AVAILABLE, "rank": int(row["rank"]), "reasonType": row["reason_type"], "reasonText": row["reason_text"]})
    return {"recommendationId": run_id, "source": source, "sourceLabel": label, "personalizationState": state, "algorithmVersion": ALGORITHM_VERSION, "catalogVersion": CATALOG_VERSION, "favoriteRevision": favorite_revision, "fallbackReason": fallback_reason, "items": items, "retryable": True}


def generate_recommendations(connection: sqlite3.Connection, user_id: str | None, limit: int = 10, *, service_available: bool = True) -> dict[str, Any]:
    limit = max(1, min(20, int(limit)))
    if not service_available:
        return _fallback(connection, user_id, "SERVICE_UNAVAILABLE", limit)
    all_rows = _catalog_rows(connection)
    if not all_rows:
        run_id = str(uuid.uuid4()); connection.execute("INSERT INTO recommendation_runs VALUES(?,?,?,?,?,?,?,?)", (run_id, user_id, "FALLBACK_POPULAR", ALGORITHM_VERSION, CATALOG_VERSION, None, "NO_PERSONALIZED_RESULTS", _now()))
        return _response(connection, run_id, "FALLBACK_POPULAR", "暂无可用电影", "NOT_APPLICABLE", None, "NO_PERSONALIZED_RESULTS", [])
    favorites, revision = _favorite_ids(connection, user_id)
    if not user_id:
        return _fallback(connection, None, "GUEST", limit)
    usable = [r for r in all_rows if r["movie_id"] in favorites and (_parse(r, "genres_json") or _parse(r, "themes_json"))]
    if len(usable) < 2:
        return _fallback(connection, user_id, "INSUFFICIENT_FAVORITES" if len(favorites) < 2 else "INSUFFICIENT_FEATURES", limit)
    genre_counts = Counter(g for r in usable for g in _parse(r, "genres_json")); theme_counts = Counter(t for r in usable for t in _parse(r, "themes_json"))
    candidates = [r for r in all_rows if r["movie_id"] not in favorites and r["movie_id"] not in _paid_ids(connection, user_id)]
    collaborative_scores = _collaborative_scores(connection, user_id, favorites, candidates)
    scored = []
    for row in candidates:
        genres, themes = set(_parse(row, "genres_json")), set(_parse(row, "themes_json"))
        matches_g = sorted(genres & set(genre_counts)); matches_t = sorted(themes & set(theme_counts))
        content_score = sum(genre_counts[g] * 10 for g in matches_g) + sum(theme_counts[t] * 8 for t in matches_t)
        content_score += math.log1p(max(0.0, float(row["popularity"]))) + 2.0 * _freshness_score(row["release_date"])
        collaborative_score = collaborative_scores.get(int(row["movie_id"]), 0.0)
        score = content_score + 12.0 * collaborative_score
        if not matches_g and not matches_t and not collaborative_score: continue
        if collaborative_score > 0 and not matches_g and not matches_t:
            reason_type, matched, text = "COLLABORATIVE", ["相似用户行为"], "与你兴趣相似的用户也喜欢这部电影"
        elif matches_g and (not matches_t or max(genre_counts[g] for g in matches_g) >= max(theme_counts[t] for t in matches_t)):
            reason_type, matched, text = "SHARED_GENRE", matches_g, "与你收藏的" + "、".join(matches_g) + "电影相似"
        else:
            reason_type, matched, text = "SHARED_THEME", matches_t, "与你收藏的" + "、".join(matches_t) + "主题相似"
        scored.append((score, float(row["popularity"]), int(row["movie_id"]), row, reason_type, matched, text))
    scored.sort(key=lambda x: (-x[0], -x[1], x[2]))
    if not scored:
        return _fallback(connection, user_id, "NO_PERSONALIZED_RESULTS", limit)
    selected = scored[:limit]; run_id = str(uuid.uuid4())
    connection.execute("INSERT INTO recommendation_runs VALUES(?,?,?,?,?,?,?,?)", (run_id, user_id, "PERSONALIZED", ALGORITHM_VERSION, CATALOG_VERSION, revision, None, _now()))
    for rank, (score, _, _, row, typ, matched, text) in enumerate(selected, 1):
        connection.execute("INSERT INTO recommendation_items VALUES(?,?,?,?,?,?,?)", (run_id, row["movie_id"], rank, score, typ, text, json.dumps(matched, ensure_ascii=False)))
    return _response(connection, run_id, "PERSONALIZED", "根据你的收藏推荐", "CURRENT", revision, None, [x[3] for x in selected])


def record_event(connection: sqlite3.Connection, recommendation_id: str, user_id: str | None, event_id: str, movie_id: int, action: str) -> bool:
    if action not in {"IMPRESSION", "CLICK"} or not isinstance(event_id, str) or not event_id or movie_id < 1:
        return False
    run = connection.execute("SELECT * FROM recommendation_runs WHERE id=? AND (user_id IS NULL OR user_id=?)", (recommendation_id, user_id)).fetchone()
    if not run or not connection.execute("SELECT 1 FROM recommendation_items WHERE run_id=? AND movie_id=?", (recommendation_id, movie_id)).fetchone():
        return False
    rank = connection.execute("SELECT rank FROM recommendation_items WHERE run_id=? AND movie_id=?", (recommendation_id, movie_id)).fetchone()[0]
    connection.execute("INSERT OR IGNORE INTO recommendation_metrics VALUES(?,?,?,?,?,?,?,?)", (event_id, recommendation_id, movie_id, run["algorithm_version"], run["source"], rank, action, _now()))
    return True
