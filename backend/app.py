import json
import hmac
import hashlib
import os
import sqlite3
import uuid
from contextlib import contextmanager
from datetime import timedelta
from pathlib import Path
from typing import Any, Iterator

from flask import Flask, jsonify, request

from auth import (
    clear_login_failures, create_session, hash_password, normalize_username,
    parse_bearer_header, record_login_failure, resolve_session, throttle_status,
    token_digest, to_timestamp, utc_now, validate_email, validate_password,
    validate_username, verify_password,
)
from showtimes import (
    VALID_SORTS, QueryValidationError, available_dates, initialize_showtime_schema,
    iso_utc, parse_local_date, parse_local_time, query_cinemas,
    seed_development_data, utc_now as showtime_utc_now, validate_showtime,
)
from seats import create_lock, get_lock, initialize_seat_schema, release_lock, release_user_locks, refund_order_seats, seats_snapshot, seed_seats
from orders import initialize_order_schema, quote_for_lock, create_order, order_json, refund_order
from payments import start_attempt, apply_provider_result, expire_pending_orders
from favorites import initialize_favorite_schema, list_changes, process_operation, migrate_legacy_favorites
from recommendations import initialize_recommendation_schema, seed_movie_catalog, sync_maoyan_catalog, generate_recommendations, record_event, MAOYAN_CATALOG_VERSION

SCHEMA_VERSION = 2
DEFAULT_SESSION_DAYS = 30
DEFAULT_FAILURE_WINDOW_MINUTES = 15
DEFAULT_FAILURE_LIMIT = 5
DEFAULT_BLOCK_MINUTES = 15

EMPTY_SYNC_DATA = {
    "name": "", "avatarUri": None, "favorites": [], "orders": [],
    "ratings": [], "comments": [], "globalComments": [], "likedCommentIds": [],
}


def create_app(database_path: str | Path | None = None,
               test_config: dict[str, Any] | None = None) -> Flask:
    app = Flask(__name__)
    app.json.ensure_ascii = False
    default_path = Path(__file__).resolve().parent / "data" / "movies_backend.db"
    configured_path = database_path or os.getenv("MOVIES_BACKEND_DB", default_path)
    app.config.from_mapping(
        DATABASE=str(Path(configured_path).resolve()),
        SESSION_LIFETIME=timedelta(days=DEFAULT_SESSION_DAYS),
        FAILURE_WINDOW=timedelta(minutes=DEFAULT_FAILURE_WINDOW_MINUTES),
        FAILURE_LIMIT=DEFAULT_FAILURE_LIMIT,
        BLOCK_DURATION=timedelta(minutes=DEFAULT_BLOCK_MINUTES),
        SCHEMA_VERSION=SCHEMA_VERSION,
        MIGRATION_FAILURE_HOOK=None,
        NOW_PROVIDER=showtime_utc_now,
    )
    if test_config:
        app.config.update(test_config)
    initialize_database(app.config["DATABASE"], app.config["SCHEMA_VERSION"],
                        app.config.get("MIGRATION_FAILURE_HOOK"), app.config["NOW_PROVIDER"]())

    @app.get("/health")
    def health():
        return jsonify(status="ok")

    @app.get("/v1/movies/<int:movie_id>/showtime-dates")
    def showtime_dates(movie_id: int):
        city_code = (request.args.get("cityCode") or "").strip()
        if movie_id < 1 or not city_code:
            return api_error("INVALID_QUERY", "movieId 和 cityCode 必须有效", 400)
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            dates = available_dates(connection, movie_id, city_code, now)
        return jsonify(movieId=movie_id, cityCode=city_code, serverTime=iso_utc(now), dates=dates)

    @app.get("/v1/movies/<int:movie_id>/cinema-showtimes")
    def cinema_showtimes(movie_id: int):
        try:
            if movie_id < 1:
                raise QueryValidationError("INVALID_MOVIE", "movieId 必须为正数", "movieId")
            city_code = (request.args.get("cityCode") or "").strip()
            if not city_code:
                raise QueryValidationError("INVALID_CITY", "cityCode 不能为空", "cityCode")
            local_date = parse_local_date(request.args.get("date"))
            start_time = parse_local_time(request.args.get("startTime"), "startTime")
            end_time = parse_local_time(request.args.get("endTime"), "endTime")
            if start_time and end_time and end_time <= start_time:
                raise QueryValidationError("INVALID_TIME_RANGE", "endTime 必须晚于 startTime", "endTime")
            minimum = optional_int("minPriceMinor")
            maximum = optional_int("maxPriceMinor")
            if minimum is not None and minimum < 0 or maximum is not None and maximum < 0:
                raise QueryValidationError("INVALID_PRICE", "价格不能为负数", "minPriceMinor")
            if minimum is not None and maximum is not None and maximum < minimum:
                raise QueryValidationError("INVALID_PRICE_RANGE", "最高价不能低于最低价", "maxPriceMinor")
            requested_sort = request.args.get("sort", "recommended")
            if requested_sort not in VALID_SORTS:
                raise QueryValidationError("INVALID_SORT", "sort 不受支持", "sort")
            latitude, longitude = optional_float("latitude"), optional_float("longitude")
            if (latitude is None) != (longitude is None):
                raise QueryValidationError("INVALID_LOCATION", "经纬度必须同时提供", "latitude")
            if latitude is not None and not -90 <= latitude <= 90 or longitude is not None and not -180 <= longitude <= 180:
                raise QueryValidationError("INVALID_LOCATION", "经纬度超出范围", "latitude")
            now = app.config["NOW_PROVIDER"]()
            with connect(app.config["DATABASE"]) as connection:
                result = query_cinemas(
                    connection, movie_id, city_code, local_date, now,
                    set(request.args.getlist("district")), start_time, end_time,
                    minimum, maximum, requested_sort, latitude, longitude,
                )
            return jsonify(movieId=movie_id, localDate=local_date.isoformat(), cityCode=city_code,
                           serverTime=iso_utc(now), **result)
        except QueryValidationError as error:
            return api_error(error.code, error.message, 400, error.field)

    @app.get("/v1/showtimes/<showtime_id>/validation")
    def showtime_validation(showtime_id: str):
        try:
            observed = optional_int("observedVersion", required=True)
            if observed is None or observed < 1:
                raise QueryValidationError("INVALID_VERSION", "observedVersion 必须为正整数", "observedVersion")
        except QueryValidationError as error:
            return api_error(error.code, error.message, 400, error.field)
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            result = validate_showtime(connection, showtime_id, observed, now)
        return api_error("SHOWTIME_NOT_FOUND", "场次不存在", 404) if result is None else jsonify(result)

    @app.get("/v1/showtimes/<showtime_id>/seats")
    def showtime_seats(showtime_id: str):
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            expire_pending_orders(connection, now)
            snapshot = seats_snapshot(connection, showtime_id, resolved[0]["id"], now)
        return api_error("SHOWTIME_NOT_FOUND", "场次不存在", 404) if snapshot is None else jsonify(snapshot)

    @app.post("/v1/showtimes/<showtime_id>/seat-locks")
    def seat_locks(showtime_id: str):
        body = request.get_json(silent=True)
        key = request.headers.get("Idempotency-Key", "")
        if not isinstance(body, dict) or not 16 <= len(key) <= 128 or not isinstance(body.get("seatIds"), list):
            return api_error("INVALID_REQUEST", "请求参数无效", 400)
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            status, result = create_lock(connection, showtime_id, resolved[0]["id"], key, body["seatIds"], now)
        return jsonify(result), status

    @app.get("/v1/seat-locks/<lock_id>")
    def seat_lock(lock_id: str):
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            result = get_lock(connection, lock_id, resolved[0]["id"], now)
        return api_error("LOCK_NOT_FOUND", "锁定不存在", 404) if result is None else jsonify(result)

    @app.delete("/v1/seat-locks/<lock_id>")
    def delete_seat_lock(lock_id: str):
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            result = release_lock(connection, lock_id, resolved[0]["id"], now)
        return api_error("LOCK_NOT_FOUND", "锁定不存在", 404) if result is None else jsonify(result)

    @app.delete("/v1/users/me/seat-locks")
    def release_my_seat_locks():
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            released = release_user_locks(connection, resolved[0]["id"], now)
        return jsonify(releasedCount=released, serverTime=iso_utc(now))

    @app.get("/v1/seat-locks/<lock_id>/order-quote")
    def order_quote(lock_id: str):
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            quote = quote_for_lock(connection, lock_id, resolved[0]["id"], now)
        return api_error("LOCK_INVALID", "座位锁无效或已过期", 409) if quote is None else jsonify(quote)

    @app.post("/v1/orders")
    def create_order_endpoint():
        body = request.get_json(silent=True) or {}
        key = request.headers.get("Idempotency-Key", "")
        if not isinstance(body.get("lockId"), str) or not isinstance(body.get("acceptedQuoteVersion"), int) or not 16 <= len(key) <= 128:
            return api_error("INVALID_REQUEST", "Invalid order request", 400)
        now = app.config["NOW_PROVIDER"]()
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"), now)
            if resolved is None: return unauthenticated()
            status, result = create_order(connection, body["lockId"], resolved[0]["id"], body["acceptedQuoteVersion"], now)
        return jsonify(result), status

    @app.get('/v1/orders/<order_id>')
    def get_order_endpoint(order_id: str):
        now=app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved=resolve_session(connection,request.headers.get('Authorization'),now)
            if resolved is None:return unauthenticated()
            expire_pending_orders(connection, now)
            order=connection.execute('SELECT * FROM orders WHERE id=? AND user_id=?',(order_id,resolved[0]['id'])).fetchone()
            if not order:return api_error('ORDER_NOT_FOUND','Order not found',404)
            return jsonify(order_json(connection, order))

    @app.get('/v1/orders')
    def list_orders_endpoint():
        try:
            limit = max(1, min(50, int(request.args.get('limit', '20'))))
        except ValueError:
            return api_error('INVALID_QUERY','limit must be an integer',400)
        now=app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved=resolve_session(connection,request.headers.get('Authorization'),now)
            if resolved is None:return unauthenticated()
            expire_pending_orders(connection, now)
            rows=connection.execute('SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC LIMIT ?',(resolved[0]['id'],limit+1)).fetchall()
            page=rows[:limit]
            return jsonify(orders=[order_json(connection, o) for o in page], nextCursor=(page[-1]['created_at'] if len(rows)>limit else None))

    @app.get('/v1/orders/<order_id>/ticket')
    def ticket_endpoint(order_id: str):
        now=app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved=resolve_session(connection,request.headers.get('Authorization'),now)
            if resolved is None:return unauthenticated()
            ticket=connection.execute('SELECT t.* FROM tickets t JOIN orders o ON o.id=t.order_id WHERE t.order_id=? AND o.user_id=?',(order_id,resolved[0]['id'])).fetchone()
            order=connection.execute('SELECT 1 FROM orders WHERE id=? AND user_id=?',(order_id,resolved[0]['id'])).fetchone()
        if not order:return api_error('ORDER_NOT_FOUND','Order not found',404)
        if not ticket:
            with connect(app.config['DATABASE']) as connection:
                paid=connection.execute("SELECT status FROM orders WHERE id=? AND user_id=?",(order_id,resolved[0]['id'])).fetchone()
                if paid and paid['status']=='PAID':
                    connection.execute('INSERT OR IGNORE INTO tickets VALUES (?,?,?,?,?)',(str(uuid.uuid4()),order_id,'READY','ticket-'+order_id,iso_utc(now)))
                    ticket=connection.execute('SELECT * FROM tickets WHERE order_id=?',(order_id,)).fetchone()
        if not ticket:return api_error('TICKET_UNAVAILABLE','Ticket is available only after payment',409)
        return jsonify(id=ticket['id'],orderId=ticket['order_id'],status=ticket['status'],credential=ticket['credential'],issuedAt=ticket['issued_at'])

    @app.post('/v1/orders/<order_id>/refund')
    def refund_order_endpoint(order_id: str):
        now = app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved = resolve_session(connection, request.headers.get('Authorization'), now)
            if resolved is None: return unauthenticated()
            order = refund_order(connection, order_id, resolved[0]['id'], now)
            if order is None: return api_error('ORDER_NOT_REFUNDABLE', '订单当前不可退票', 409)
            refund_order_seats(
                connection,
                order['seat_lock_id'],
                order['id'],
                resolved[0]['id'],
                now,
            )
            return jsonify(order_json(connection, order))

    @app.get('/v1/tickets')
    def list_tickets_endpoint():
        now = app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved = resolve_session(connection, request.headers.get('Authorization'), now)
            if resolved is None: return unauthenticated()
            rows = connection.execute(
                "SELECT t.*,o.total_minor,o.currency,o.paid_at,o.created_at,c.name cinema_name,a.name auditorium_name,"
                "s.id showtime_id,s.starts_at,c.time_zone,s.movie_id,m.title movie_title,m.poster_url "
                "FROM tickets t JOIN orders o ON o.id=t.order_id "
                "JOIN seat_locks l ON l.id=o.seat_lock_id JOIN showtimes s ON s.id=l.showtime_id "
                "JOIN cinemas c ON c.id=s.cinema_id JOIN auditoriums a ON a.id=s.auditorium_id "
                "LEFT JOIN movie_catalog m ON m.movie_id=s.movie_id "
                "WHERE o.user_id=? AND o.status='PAID' ORDER BY t.issued_at DESC", (resolved[0]['id'],)).fetchall()
            tickets = []
            for row in rows:
                items = connection.execute("SELECT row_label,seat_label FROM order_items WHERE order_id=? ORDER BY seat_id", (row['order_id'],)).fetchall()
                tickets.append({
                    'id': row['id'], 'ticketId': row['id'], 'orderId': row['order_id'], 'status': row['status'],
                    'credential': row['credential'], 'issuedAt': row['issued_at'], 'movieId': row['movie_id'], 'posterUrl': row['poster_url'],
                    'movieTitle': row['movie_title'] or ('Movie ' + str(row['movie_id'])), 'cinemaName': row['cinema_name'],
                    'auditoriumName': row['auditorium_name'], 'showtimeId': row['showtime_id'],
                    'startsAt': row['starts_at'], 'timeZone': row['time_zone'],
                    'seats': [f"{item['row_label']}{item['seat_label']}" for item in items],
                    'total': {'amountMinor': row['total_minor'], 'currency': row['currency']},
                    'paidAt': row['paid_at'] or row['issued_at']
                })
            return jsonify(tickets=tickets)

    @app.post('/v1/orders/<order_id>/payment-attempts')
    def payment_attempt_endpoint(order_id: str):
        body=request.get_json(silent=True) or {}; key=request.headers.get('Idempotency-Key','')
        if body.get('method') not in ('ALIPAY_SIMULATED','WECHAT_SIMULATED') or not 16<=len(key)<=128:return api_error('INVALID_REQUEST','Invalid payment request',400)
        now=app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved=resolve_session(connection,request.headers.get('Authorization'),now)
            if resolved is None:return unauthenticated()
            attempt=start_attempt(connection,order_id,resolved[0]['id'],body['method'],key,now)
            if attempt is None:return api_error('ORDER_NOT_FOUND','Order not found',404)
            if attempt is False:return api_error('ORDER_NOT_PAYABLE','Order cannot be paid',409)
            order_row=connection.execute('SELECT total_minor,currency FROM orders WHERE id=?',(attempt['order_id'],)).fetchone()
            return jsonify(id=attempt['id'],orderId=attempt['order_id'],method=attempt['method'],status=attempt['status'],transactionRef=attempt['transaction_ref'],amount={'amountMinor': order_row['total_minor'],'currency': order_row['currency']},createdAt=attempt['created_at']),201

    @app.post('/v1/orders/<order_id>/payment-confirmations')
    def payment_confirmation_endpoint(order_id: str):
        body = request.get_json(silent=True) or {}
        attempt_id = body.get('paymentAttemptId')
        if not isinstance(attempt_id, str) or not attempt_id:
            return api_error('INVALID_REQUEST', 'paymentAttemptId is required', 400)
        now = app.config['NOW_PROVIDER']()
        with connect(app.config['DATABASE']) as connection:
            resolved = resolve_session(connection, request.headers.get('Authorization'), now)
            if resolved is None: return unauthenticated()
            attempt = connection.execute(
                'SELECT * FROM payment_attempts WHERE id=? AND order_id=?', (attempt_id, order_id)).fetchone()
            if not attempt: return api_error('PAYMENT_ATTEMPT_INVALID', 'Payment attempt does not belong to this order', 409)
            order = connection.execute('SELECT * FROM orders WHERE id=? AND user_id=?', (order_id, resolved[0]['id'])).fetchone()
            if not order: return api_error('ORDER_NOT_FOUND', 'Order not found', 404)
            if attempt['status'] == 'SUCCEEDED' or order['status'] == 'PAID':
                final = order
            else:
                final = apply_provider_result(connection, attempt['transaction_ref'], 'SUCCEEDED', now)
                if final is False: return api_error('ORDER_NOT_PAYABLE', 'Seat lock is no longer valid', 409)
            ticket = connection.execute('SELECT * FROM tickets WHERE order_id=?', (order_id,)).fetchone()
            return jsonify(orderId=order_id, paymentAttemptId=attempt_id, orderStatus=final['status'],
                           paymentStatus='SUCCEEDED' if final['status'] == 'PAID' else attempt['status'],
                           ticketReady=bool(ticket), ticketId=ticket['id'] if ticket else None,
                           serverTimestamp=iso_utc(now))

    @app.post('/v1/payment-provider/webhooks')
    def payment_webhook():
        body=request.get_json(silent=True) or {}
        if body.get('status') not in ('SUCCEEDED','FAILED','CANCELLED','PENDING','UNKNOWN') or not isinstance(body.get('transactionRef'),str):return api_error('INVALID_CALLBACK','Invalid callback',400)
        secret=os.getenv('PAYMENT_WEBHOOK_SECRET')
        signature=request.headers.get('X-Payment-Signature')
        if secret and (not signature or not hmac.compare_digest(signature,hmac.new(secret.encode(),request.get_data(),hashlib.sha256).hexdigest())):
            return api_error('INVALID_SIGNATURE','Invalid callback signature',401)
        with connect(app.config['DATABASE']) as connection:
            event_id=body.get('eventId') or ('legacy-'+body['transactionRef']+'-'+body['status'])
            if connection.execute('SELECT 1 FROM payment_events WHERE id=?',(event_id,)).fetchone():
                existing_order=connection.execute('SELECT o.status FROM orders o JOIN payment_attempts p ON p.order_id=o.id WHERE p.transaction_ref=?',(body['transactionRef'],)).fetchone()
                return jsonify(accepted=True,duplicate=True,status=existing_order['status'] if existing_order else 'UNKNOWN')
            connection.execute('INSERT INTO payment_events VALUES (?,?,?,?)',(event_id,body['transactionRef'],body['status'],iso_utc(app.config['NOW_PROVIDER']())))
            order=apply_provider_result(connection,body['transactionRef'],body['status'],app.config['NOW_PROVIDER']())
        if order is None:return api_error('TRANSACTION_NOT_FOUND','Transaction not found',404)
        if order is False:return api_error('ORDER_NOT_PAYABLE','Order cannot be paid',409)
        return jsonify(id=order['id'],status=order['status'])

    @app.post("/auth/register")
    def register():
        body = request.get_json(silent=True)
        if not isinstance(body, dict):
            return validation_error({"body": "请求体必须是 JSON 对象"})
        errors: dict[str, str] = {}
        username = validate_username(body.get("username"))
        email = validate_email(body.get("email"))
        password = validate_password(body.get("password"))
        if username is None:
            errors["username"] = "用户名长度必须为 2–30 个字符"
        if email is None:
            errors["email"] = "请输入有效邮箱"
        if password is None:
            errors["password"] = "密码长度必须为 8–64 个字符"
        if body.get("confirmPassword") != body.get("password"):
            errors["confirmPassword"] = "两次输入的密码不一致"
        if errors:
            return validation_error(errors)
        now = to_timestamp(utc_now())
        user_id = str(uuid.uuid4())
        try:
            with connect(app.config["DATABASE"]) as connection:
                connection.execute(
                    "INSERT INTO user_accounts(id,email_normalized,email_display,username,password_hash,status,created_at,updated_at) "
                    "VALUES (?,?,?,?,?,'active',?,?)",
                    (user_id, email, email, username, hash_password(password), now, now),
                )
                token, expires_at = create_session(connection, user_id, app.config["SESSION_LIFETIME"])
        except sqlite3.IntegrityError:
            return error_response("email_exists", "该邮箱已注册", 409)
        return jsonify(user=user_json(user_id, email, username), token=token,
                       expiresAt=expires_at), 201

    @app.post("/auth/login")
    def login():
        body = request.get_json(silent=True)
        if not isinstance(body, dict):
            return validation_error({"body": "请求体必须是 JSON 对象"})
        email = validate_email(body.get("email"))
        password = body.get("password") if isinstance(body.get("password"), str) else ""
        if email is None or not 1 <= len(password) <= 64:
            errors = {}
            if email is None:
                errors["email"] = "请输入有效邮箱"
            if not password:
                errors["password"] = "密码不能为空"
            return validation_error(errors)
        source = request.remote_addr or "unknown"
        with connect(app.config["DATABASE"]) as connection:
            _, _, retry_after = throttle_status(connection, email, source,
                                                 app.config["FAILURE_WINDOW"])
            if retry_after:
                return rate_limited(retry_after)
            user = connection.execute(
                "SELECT * FROM user_accounts WHERE email_normalized=? AND status='active'", (email,)
            ).fetchone()
            if user is None or not verify_password(user["password_hash"], password):
                retry_after = record_login_failure(
                    connection, email, source, app.config["FAILURE_WINDOW"],
                    int(app.config["FAILURE_LIMIT"]), app.config["BLOCK_DURATION"])
                return rate_limited(retry_after) if retry_after else error_response(
                    "invalid_credentials", "邮箱或密码错误", 401)
            clear_login_failures(connection, email, source)
            token, expires_at = create_session(connection, user["id"], app.config["SESSION_LIFETIME"])
            response_user = user_json(user["id"], user["email_display"], user["username"])
        return jsonify(user=response_user, token=token, expiresAt=expires_at)

    @app.get("/auth/me")
    def me():
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            if resolved is None:
                return unauthenticated()
            user, _ = resolved
            return jsonify(user_json(user["id"], user["email_display"], user["username"]))

    @app.post("/auth/logout")
    def logout():
        token = parse_bearer_header(request.headers.get("Authorization"))
        if token:
            with connect(app.config["DATABASE"]) as connection:
                resolved = resolve_session(connection, request.headers.get("Authorization"))
                if resolved is not None:
                    release_user_locks(connection, resolved[0]["id"], app.config["NOW_PROVIDER"]())
                connection.execute(
                    "UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,?) WHERE token_hash=?",
                    (to_timestamp(utc_now()), token_digest(token)))
        return "", 204

    @app.post("/sync/push")
    def push_sync_data():
        body = request.get_json(silent=True)
        if not isinstance(body, dict):
            return validation_error({"body": "请求体必须是 JSON 对象"})
        if any(key in body for key in ("email", "password", "data")):
            return validation_error({"body": "同步数据不得包含身份或密码字段"})
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            if resolved is None:
                return unauthenticated()
            user, _ = resolved
            email = user["email_normalized"]
            normalized = normalize_sync_data(body)
            comments = merge_comments(normalized.get("comments", []))
            normalized["globalComments"] = []
            now = to_timestamp(utc_now())
            connection.execute(
                "INSERT INTO user_sync_data(email,payload,updated_at) VALUES (?,?,?) "
                "ON CONFLICT(email) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at",
                (email, json.dumps(normalized, ensure_ascii=False), now))
            for comment in comments:
                connection.execute(
                    "INSERT INTO global_comments(id,payload,updated_at) VALUES (?,?,?) "
                    "ON CONFLICT(id) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at",
                    (comment["id"], json.dumps(comment, ensure_ascii=False), now))
        return jsonify(status="ok", updatedAt=now)

    @app.get("/sync/pull")
    def pull_sync_data():
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            if resolved is None:
                return unauthenticated()
            user, _ = resolved
            row = connection.execute("SELECT payload FROM user_sync_data WHERE email=?",
                                     (user["email_normalized"],)).fetchone()
            comment_rows = connection.execute("SELECT payload FROM global_comments ORDER BY id").fetchall()
            user_rows = connection.execute("SELECT payload FROM user_sync_data").fetchall()
        data = normalize_sync_data(json.loads(row["payload"]) if row else {"name": user["username"]})
        like_counts: dict[int, int] = {}
        for stored in user_rows:
            liked_ids = json.loads(stored["payload"]).get("likedCommentIds", [])
            if isinstance(liked_ids, list):
                for comment_id in set(liked_ids):
                    if isinstance(comment_id, int):
                        like_counts[comment_id] = like_counts.get(comment_id, 0) + 1
        comments = [json.loads(stored["payload"]) for stored in comment_rows]
        for comment in comments:
            comment["likes"] = like_counts.get(comment.get("id"), 0)
        data["globalComments"] = comments
        return jsonify(data)

    @app.get("/v1/favorites")
    def favorites_list_endpoint():
        try:
            after = int(request.args.get("afterRevision", "0")); limit = int(request.args.get("limit", "100"))
            if after < 0 or not 1 <= limit <= 200: raise ValueError
        except ValueError:
            return api_error("INVALID_QUERY", "分页参数无效", 400)
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            if resolved is None: return unauthenticated()
            if not app.config.get("TESTING") and not connection.execute("SELECT 1 FROM movie_catalog WHERE catalog_version=? LIMIT 1", (MAOYAN_CATALOG_VERSION,)).fetchone():
                sync_maoyan_catalog(connection)
            return jsonify(list_changes(connection, resolved[0]["id"], after, limit))

    @app.put("/v1/favorites/<int:movie_id>")
    def favorite_set_endpoint(movie_id: int):
        if movie_id < 1: return api_error("INVALID_MOVIE", "movieId 必须为正数", 400)
        key = request.headers.get("Idempotency-Key", "")
        body = request.get_json(silent=True)
        if not isinstance(body, dict) or not 16 <= len(key) <= 128:
            return api_error("INVALID_REQUEST", "请求参数无效", 400)
        if set(body) - {"targetState", "deviceId", "localSequence"} or not isinstance(body.get("targetState"), bool) \
                or not isinstance(body.get("deviceId"), str) or not 16 <= len(body["deviceId"]) <= 128 \
                or not isinstance(body.get("localSequence"), int) or body["localSequence"] < 1:
            return api_error("INVALID_REQUEST", "请求参数无效", 400)
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            if resolved is None: return unauthenticated()
            if not app.config.get("TESTING") and not connection.execute("SELECT 1 FROM movie_catalog WHERE catalog_version=? LIMIT 1", (MAOYAN_CATALOG_VERSION,)).fetchone():
                sync_maoyan_catalog(connection)
            result = process_operation(connection, resolved[0]["id"], {"clientOperationId": key, "movieId": movie_id, **body}, body["deviceId"])
        return jsonify(result), 200 if result["result"] != "REJECTED" else 409

    @app.post("/v1/favorites/sync")
    def favorite_sync_endpoint():
        body = request.get_json(silent=True)
        if not isinstance(body, dict) or not isinstance(body.get("deviceId"), str) or not 16 <= len(body["deviceId"]) <= 128:
            return api_error("INVALID_REQUEST", "请求参数无效", 400)
        if set(body) - {"deviceId", "sinceRevision", "operations"}:
            return api_error("INVALID_REQUEST", "请求不得包含身份字段", 400)
        operations = body.get("operations"); since = body.get("sinceRevision")
        if not isinstance(operations, list) or len(operations) > 100 or not isinstance(since, int) or since < 0:
            return api_error("INVALID_REQUEST", "请求参数无效", 400)
        sequences = [op.get("localSequence") if isinstance(op, dict) else None for op in operations]
        if any(not isinstance(s, int) or s < 1 for s in sequences) or sequences != sorted(set(sequences)):
            return api_error("INVALID_SEQUENCE", "操作必须按 localSequence 严格递增且不重复", 400)
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            if resolved is None: return unauthenticated()
            if not app.config.get("TESTING") and not connection.execute("SELECT 1 FROM movie_catalog WHERE catalog_version=? LIMIT 1", (MAOYAN_CATALOG_VERSION,)).fetchone():
                sync_maoyan_catalog(connection)
            results = [process_operation(connection, resolved[0]["id"], op, body["deviceId"], require_sequence=True) for op in operations]
            response = list_changes(connection, resolved[0]["id"], since, 200)
            response["operationResults"] = results
            return jsonify(response)

    @app.get("/v1/recommendations")
    def recommendations_endpoint():
        try:
            limit = int(request.args.get("limit", "10"))
            if not 1 <= limit <= 20:
                raise ValueError
        except ValueError:
            return api_error("INVALID_QUERY", "limit 必须是 1 到 20 之间的整数", 400)
        with connect(app.config["DATABASE"]) as connection:
            # Production recommendations must come only from the latest
            # successful Maoyan catalog snapshot. Keep deterministic seed data
            # available for tests, but never mix it into live responses.
            if not app.config.get("TESTING"):
                sync_maoyan_catalog(connection)
                connection.execute(
                    "UPDATE movie_catalog SET availability='UNAVAILABLE' WHERE catalog_version<>?",
                    (MAOYAN_CATALOG_VERSION,),
                )
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            user_id = resolved[0]["id"] if resolved else None
            return jsonify(generate_recommendations(connection, user_id, limit))

    @app.post("/v1/recommendations/<recommendation_id>/events")
    def recommendation_event_endpoint(recommendation_id: str):
        body = request.get_json(silent=True)
        if not isinstance(body, dict) or not isinstance(body.get("eventId"), str) or not isinstance(body.get("movieId"), int) or body.get("movieId") < 1 or body.get("action") not in ("IMPRESSION", "CLICK"):
            return api_error("INVALID_REQUEST", "eventId、movieId 或 action 无效", 400)
        with connect(app.config["DATABASE"]) as connection:
            resolved = resolve_session(connection, request.headers.get("Authorization"))
            user_id = resolved[0]["id"] if resolved else None
            if not record_event(connection, recommendation_id, user_id, body["eventId"], body["movieId"], body["action"]):
                return api_error("INVALID_EVENT", "推荐事件与结果不匹配", 400)
        return jsonify(accepted=True), 202

    return app


def user_json(user_id: str, email: str, username: str,
              avatar_uri: str | None = None) -> dict[str, Any]:
    return {"id": user_id, "email": email, "username": username, "avatarUri": avatar_uri}


def optional_int(name: str, required: bool = False) -> int | None:
    value = request.args.get(name)
    if value is None:
        if required: raise QueryValidationError("MISSING_PARAMETER", f"{name} 不能为空", name)
        return None
    try: return int(value)
    except ValueError as error: raise QueryValidationError("INVALID_INTEGER", f"{name} 必须是整数", name) from error


def optional_float(name: str) -> float | None:
    value = request.args.get(name)
    if value is None: return None
    try: return float(value)
    except ValueError as error: raise QueryValidationError("INVALID_NUMBER", f"{name} 必须是数字", name) from error


def api_error(code: str, message: str, status: int, field: str | None = None):
    return jsonify(code=code, message=message, field=field), status


def error_response(code: str, message: str, status: int, **extra: Any):
    return jsonify(code=code, message=message, **extra), status


def validation_error(fields: dict[str, str]):
    return error_response("validation_error", "请检查输入内容", 400, fieldErrors=fields)


def unauthenticated():
    return error_response("unauthenticated", "登录状态已失效，请重新登录", 401)


def rate_limited(seconds: int):
    response = jsonify(code="rate_limited", message="尝试次数过多，请稍后再试",
                       retryAfterSeconds=seconds)
    response.status_code = 429
    response.headers["Retry-After"] = str(seconds)
    return response


@contextmanager
def connect(database_path: str) -> Iterator[sqlite3.Connection]:
    connection = sqlite3.connect(database_path, timeout=10)
    try:
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def initialize_database(database_path: str, schema_version: int = SCHEMA_VERSION,
                        failure_hook=None, showtime_seed_now=None) -> None:
    Path(database_path).parent.mkdir(parents=True, exist_ok=True)
    with connect(database_path) as connection:
        connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS schema_metadata(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS user_sync_data(email TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL,updated_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS global_comments(id INTEGER PRIMARY KEY NOT NULL,payload TEXT NOT NULL,updated_at TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS user_accounts(
                id TEXT PRIMARY KEY NOT NULL,
                email_normalized TEXT UNIQUE NOT NULL CHECK(length(email_normalized)>0),
                email_display TEXT NOT NULL CHECK(length(email_display)>0),
                username TEXT NOT NULL CHECK(length(username) BETWEEN 2 AND 30),
                password_hash TEXT NOT NULL CHECK(length(password_hash)>0),
                status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled')),
                created_at TEXT NOT NULL,updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS auth_sessions(
                id TEXT PRIMARY KEY NOT NULL,user_id TEXT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
                token_hash TEXT UNIQUE NOT NULL CHECK(length(token_hash)=64),created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,revoked_at TEXT,last_used_at TEXT NOT NULL,CHECK(expires_at>created_at)
            );
            CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id);
            CREATE TABLE IF NOT EXISTS auth_throttle(
                key TEXT PRIMARY KEY NOT NULL,failure_count INTEGER NOT NULL CHECK(failure_count>=0),
                window_started_at TEXT NOT NULL,blocked_until TEXT
            );
            """)
        migrate_legacy_credentials(connection, failure_hook)
        initialize_showtime_schema(connection)
        seed_development_data(connection, showtime_seed_now)
        initialize_seat_schema(connection)
        seed_seats(connection, showtime_seed_now)
        initialize_order_schema(connection)
        initialize_favorite_schema(connection)
        migrate_legacy_favorites(connection)
        initialize_recommendation_schema(connection)
        seed_movie_catalog(connection)
        connection.execute(
            "INSERT INTO schema_metadata(key,value) VALUES ('schema_version',?) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value", (str(schema_version),))


def migrate_legacy_credentials(connection: sqlite3.Connection, failure_hook=None) -> None:
    rows = connection.execute("SELECT email,payload FROM user_sync_data").fetchall()
    for index, row in enumerate(rows):
        try:
            payload = json.loads(row["payload"])
        except (TypeError, json.JSONDecodeError):
            continue
        if not isinstance(payload, dict):
            continue
        password = validate_password(payload.pop("password", None), migration=True)
        email = validate_email(payload.pop("email", None) or row["email"])
        if email and password:
            username = validate_username(payload.get("name"))
            if username is None:
                username = normalize_username(email.split("@", 1)[0])[:30]
                if len(username) < 2:
                    username = "User"
            now = to_timestamp(utc_now())
            connection.execute(
                "INSERT OR IGNORE INTO user_accounts(id,email_normalized,email_display,username,password_hash,status,created_at,updated_at) "
                "VALUES (?,?,?,?,?,'active',?,?)",
                (str(uuid.uuid4()), email, email, username, hash_password(password), now, now))
        connection.execute("UPDATE user_sync_data SET payload=? WHERE email=?",
                           (json.dumps(normalize_sync_data(payload), ensure_ascii=False), row["email"]))
        if failure_hook:
            failure_hook(index, row["email"])


def normalize_sync_data(data: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(EMPTY_SYNC_DATA)
    normalized.update({key: data.get(key, default) for key, default in EMPTY_SYNC_DATA.items()})
    for key in ("favorites", "orders", "ratings", "comments", "globalComments", "likedCommentIds"):
        if not isinstance(normalized[key], list):
            normalized[key] = []
    if not isinstance(normalized["name"], str):
        normalized["name"] = ""
    if normalized["avatarUri"] is not None and not isinstance(normalized["avatarUri"], str):
        normalized["avatarUri"] = None
    return normalized


def merge_comments(*comment_lists: list[Any]) -> list[dict[str, Any]]:
    merged: dict[int, dict[str, Any]] = {}
    for comments in comment_lists:
        for comment in comments:
            if isinstance(comment, dict) and isinstance(comment.get("id"), int):
                merged[comment["id"]] = comment
    return list(merged.values())


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
