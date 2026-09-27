"""Authoritative showtime seat inventory and temporary locks."""
from __future__ import annotations

import hashlib
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone

from auth import to_timestamp, parse_timestamp
from showtimes import iso_utc, joined_showtimes, showtime_json

LOCK_SECONDS = 600

def initialize_seat_schema(connection: sqlite3.Connection) -> None:
    connection.executescript("""
    CREATE TABLE IF NOT EXISTS auditorium_seats(
      auditorium_id TEXT NOT NULL REFERENCES auditoriums(id),seat_id TEXT NOT NULL,row_index INTEGER NOT NULL CHECK(row_index>=0),column_index INTEGER NOT NULL CHECK(column_index>=0),
      row_label TEXT NOT NULL,seat_label TEXT,position_type TEXT NOT NULL CHECK(position_type IN ('SEAT','AISLE','EMPTY','COUPLE_LEFT','COUPLE_RIGHT')),
      price_zone_id TEXT,is_usable INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(auditorium_id,seat_id),UNIQUE(auditorium_id,row_index,column_index));
    CREATE TABLE IF NOT EXISTS showtime_seats(
      showtime_id TEXT NOT NULL REFERENCES showtimes(id),seat_id TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('AVAILABLE','LOCKED','SOLD','UNAVAILABLE')),
      unit_price_minor INTEGER NOT NULL CHECK(unit_price_minor>=0),currency TEXT NOT NULL CHECK(length(currency)=3),lock_id TEXT,order_id TEXT,version INTEGER NOT NULL DEFAULT 1,updated_at TEXT NOT NULL,
      PRIMARY KEY(showtime_id,seat_id),CHECK(NOT(lock_id IS NOT NULL AND order_id IS NOT NULL)));
    CREATE TABLE IF NOT EXISTS seat_locks(
      id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES user_accounts(id),showtime_id TEXT NOT NULL REFERENCES showtimes(id),idempotency_key TEXT NOT NULL,
      seat_fingerprint TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('ACTIVE','RELEASED','EXPIRED','CONVERTED','INVALIDATED')),
      created_at TEXT NOT NULL,expires_at TEXT NOT NULL,released_at TEXT,release_reason TEXT,total_price_minor INTEGER NOT NULL CHECK(total_price_minor>=0),currency TEXT NOT NULL,
      UNIQUE(user_id,idempotency_key));
    CREATE TABLE IF NOT EXISTS seat_lock_items(
      lock_id TEXT NOT NULL REFERENCES seat_locks(id),showtime_id TEXT NOT NULL,seat_id TEXT NOT NULL,row_label TEXT NOT NULL,seat_label TEXT NOT NULL,price_zone_id TEXT NOT NULL,
      unit_price_minor INTEGER NOT NULL CHECK(unit_price_minor>=0),currency TEXT NOT NULL,PRIMARY KEY(lock_id,seat_id));
    CREATE INDEX IF NOT EXISTS idx_showtime_seats_status ON showtime_seats(showtime_id,status);
    CREATE INDEX IF NOT EXISTS idx_seat_locks_user ON seat_locks(user_id,status,expires_at);
    """)

def seed_seats(connection: sqlite3.Connection, now: datetime) -> None:
    now = now or datetime.now(timezone.utc)
    auditorium_ids = [r["id"] for r in connection.execute("SELECT id FROM auditoriums")]
    for auditorium_id in auditorium_ids:
        # Keep every grid position in the authoritative layout: empty places and
        # aisles are presentation positions, while non-usable seats are inventory.
        for row in range(6):
            row_label = chr(65 + row)
            for col in range(8):
                if col == 4:
                    connection.execute("INSERT OR IGNORE INTO auditorium_seats VALUES (?,?,?,?,?,?,?,?,?)",
                        (auditorium_id, f"aisle-{row_label}", row, col, row_label, None, 'AISLE', None, 0))
                    continue
                if row == 0 and col == 0:
                    connection.execute("INSERT OR IGNORE INTO auditorium_seats VALUES (?,?,?,?,?,?,?,?,?)",
                        (auditorium_id, f"empty-{row_label}-{col}", row, col, row_label, None, 'EMPTY', None, 0))
                    continue
                seat_id = f"{row_label}-{col+1:02d}"
                kind = 'COUPLE_LEFT' if col == 6 else ('COUPLE_RIGHT' if col == 7 else 'SEAT')
                usable = 0 if (row == 5 and col == 0) else 1
                zone = 'couple' if kind.startswith('COUPLE') else 'standard'
                connection.execute("INSERT OR IGNORE INTO auditorium_seats VALUES (?,?,?,?,?,?,?,?,?)",
                    (auditorium_id, seat_id, row, col, row_label, str(col + 1), kind, zone, usable))
    for showtime in connection.execute("SELECT id,auditorium_id,base_price_minor,currency FROM showtimes"):
        for seat in connection.execute("SELECT seat_id,is_usable,price_zone_id FROM auditorium_seats WHERE auditorium_id=? AND position_type IN ('SEAT','COUPLE_LEFT','COUPLE_RIGHT')",(showtime['auditorium_id'],)):
            status='AVAILABLE' if seat['is_usable'] else 'UNAVAILABLE'
            connection.execute("INSERT OR IGNORE INTO showtime_seats VALUES (?,?,?,?,?,?,?,?,?)",(showtime['id'],seat['seat_id'],status,showtime['base_price_minor'],showtime['currency'],None,None,1,iso_utc(now)))

def _expire(connection: sqlite3.Connection, now: datetime) -> None:
    expired=connection.execute("SELECT id FROM seat_locks WHERE status='ACTIVE' AND expires_at<=?",(iso_utc(now),)).fetchall()
    for row in expired: _release(connection,row['id'],now,'EXPIRED','EXPIRED')

def _release(connection: sqlite3.Connection, lock_id: str, now: datetime, status: str='RELEASED', reason: str='USER') -> None:
    lock=connection.execute("SELECT * FROM seat_locks WHERE id=?",(lock_id,)).fetchone()
    if not lock or lock['status']!='ACTIVE': return
    connection.execute("UPDATE showtime_seats SET status='AVAILABLE',lock_id=NULL,version=version+1,updated_at=? WHERE showtime_id=? AND lock_id=? AND status='LOCKED'",(iso_utc(now),lock['showtime_id'],lock_id))
    connection.execute("UPDATE seat_locks SET status=?,released_at=?,release_reason=? WHERE id=?",(status,iso_utc(now),reason,lock_id))

def _lock_json(connection: sqlite3.Connection, lock: sqlite3.Row, now: datetime) -> dict:
    items=connection.execute("SELECT * FROM seat_lock_items WHERE lock_id=? ORDER BY seat_id",(lock['id'],)).fetchall()
    return {'id':lock['id'],'showtimeId':lock['showtime_id'],'status':lock['status'],'serverTime':iso_utc(now),'createdAt':lock['created_at'],'expiresAt':lock['expires_at'],'releaseReason':lock['release_reason'],'items':[{'seatId':i['seat_id'],'rowLabel':i['row_label'],'seatLabel':i['seat_label'],'priceZoneId':i['price_zone_id'],'unitPrice':{'amountMinor':i['unit_price_minor'],'currency':i['currency']}} for i in items],'totalPrice':{'amountMinor':lock['total_price_minor'],'currency':lock['currency']}}

def seats_snapshot(connection: sqlite3.Connection, showtime_id: str, user_id: str, now: datetime) -> dict | None:
    _expire(connection,now); show=joined_showtimes(connection,showtime_id=showtime_id)
    if not show: return None
    row=show[0]; positions=connection.execute("SELECT a.*,s.status,s.lock_id,s.unit_price_minor,s.currency FROM auditorium_seats a LEFT JOIN showtime_seats s ON s.seat_id=a.seat_id AND s.showtime_id=? WHERE a.auditorium_id=? ORDER BY a.row_index,a.column_index",(showtime_id,row['auditorium_id'])).fetchall(); rows={}
    for p in positions:
        mine=p['lock_id'] and connection.execute("SELECT 1 FROM seat_locks WHERE id=? AND user_id=? AND status='ACTIVE'",(p['lock_id'],user_id)).fetchone()
        status=None if p['position_type'] in ('AISLE','EMPTY') else ({'LOCKED':'LOCKED_BY_ME' if mine else 'LOCKED_BY_OTHER'}.get(p['status'],p['status']))
        rows.setdefault(p['row_index'],{'rowIndex':p['row_index'],'rowLabel':p['row_label'],'positions':[]})['positions'].append({'seatId':p['seat_id'] if status else None,'rowIndex':p['row_index'],'columnIndex':p['column_index'],'rowLabel':p['row_label'],'seatLabel':p['seat_label'],'positionType':p['position_type'],'priceZoneId':p['price_zone_id'],'price':None if status is None else {'amountMinor':p['unit_price_minor'],'currency':p['currency']},'inventoryStatus':status,'unavailableReason':None})
    version=connection.execute("SELECT COALESCE(MAX(version),0) v FROM showtime_seats WHERE showtime_id=?",(showtime_id,)).fetchone()['v']
    return {'showtimeId':showtime_id,'auditoriumId':row['auditorium_id'],'auditoriumName':row['auditorium_name'],'screenLabel':'屏幕方向','serverTime':iso_utc(now),'inventoryVersion':version,'rows':list(rows.values())}

def create_lock(connection: sqlite3.Connection, showtime_id: str, user_id: str, key: str, seat_ids: list[str], now: datetime) -> tuple[int,dict]:
    if not 1<=len(seat_ids)<=6 or len(set(seat_ids))!=len(seat_ids): return 400,{'code':'INVALID_SEATS','message':'请选择 1 到 6 个不重复座位'}
    # Session validation updates last_used_at before we reach this service. Commit that
    # harmless audit update, then acquire SQLite's write lock for the inventory change.
    connection.commit()
    connection.execute('BEGIN IMMEDIATE'); _expire(connection,now)
    fingerprint = hashlib.sha256(','.join(sorted(seat_ids)).encode()).hexdigest()
    existing=connection.execute("SELECT * FROM seat_locks WHERE user_id=? AND idempotency_key=?",(user_id,key)).fetchone()
    if existing: return 200,_lock_json(connection,existing,now)
    # A client may retry after losing the response and accidentally generate a new
    # key.  The same active selection is still one intent, never a second lock.
    existing=connection.execute("SELECT * FROM seat_locks WHERE user_id=? AND showtime_id=? AND seat_fingerprint=? AND status='ACTIVE'",(user_id,showtime_id,fingerprint)).fetchone()
    if existing: return 200,_lock_json(connection,existing,now)
    show=joined_showtimes(connection,showtime_id=showtime_id)
    if not show or not showtime_json(show[0],now)['selectable']: return 409,{'code':'SHOWTIME_UNAVAILABLE','message':'场次不可售','conflictingSeatIds':[],'latestInventoryVersion':0,'serverTime':iso_utc(now)}
    marks=','.join('?'*len(seat_ids)); available=connection.execute(f"SELECT s.*,a.row_label,a.seat_label,a.price_zone_id FROM showtime_seats s JOIN auditorium_seats a ON a.auditorium_id=? AND a.seat_id=s.seat_id WHERE s.showtime_id=? AND s.seat_id IN ({marks}) AND s.status='AVAILABLE'",(show[0]['auditorium_id'],showtime_id,*seat_ids)).fetchall()
    if len(available)!=len(seat_ids):
        version=connection.execute("SELECT COALESCE(MAX(version),0) v FROM showtime_seats WHERE showtime_id=?",(showtime_id,)).fetchone()['v']; return 409,{'code':'SEAT_CONFLICT','message':'部分座位已不可用','conflictingSeatIds':seat_ids,'latestInventoryVersion':version,'serverTime':iso_utc(now)}
    currency=available[0]['currency']; total=sum(r['unit_price_minor'] for r in available); lock_id=str(uuid.uuid4()); created=iso_utc(now); expires=iso_utc(now+timedelta(seconds=LOCK_SECONDS))
    connection.execute("INSERT INTO seat_locks VALUES (?,?,?,?,?,'ACTIVE',?,?,?,?,?,?)",(lock_id,user_id,showtime_id,key,fingerprint,created,expires,None,None,total,currency))
    for item in available:
        connection.execute("INSERT INTO seat_lock_items VALUES (?,?,?,?,?,?,?,?)",(lock_id,showtime_id,item['seat_id'],item['row_label'],item['seat_label'],item['price_zone_id'],item['unit_price_minor'],currency))
        updated = connection.execute("UPDATE showtime_seats SET status='LOCKED',lock_id=?,version=version+1,updated_at=? WHERE showtime_id=? AND seat_id=? AND status='AVAILABLE'",(lock_id,created,showtime_id,item['seat_id'])).rowcount
        if updated != 1:
            raise RuntimeError("seat inventory changed during lock transaction")
    lock=connection.execute("SELECT * FROM seat_locks WHERE id=?",(lock_id,)).fetchone(); return 201,_lock_json(connection,lock,now)

def get_lock(connection: sqlite3.Connection, lock_id: str, user_id: str, now: datetime) -> dict | None:
    _expire(connection,now); lock=connection.execute("SELECT * FROM seat_locks WHERE id=? AND user_id=?",(lock_id,user_id)).fetchone(); return _lock_json(connection,lock,now) if lock else None

def release_lock(connection: sqlite3.Connection, lock_id: str, user_id: str, now: datetime) -> dict | None:
    lock=connection.execute("SELECT * FROM seat_locks WHERE id=? AND user_id=?",(lock_id,user_id)).fetchone()
    if not lock:return None
    _release(connection,lock_id,now); lock=connection.execute("SELECT * FROM seat_locks WHERE id=?",(lock_id,)).fetchone(); return _lock_json(connection,lock,now)

def refund_order_seats(connection: sqlite3.Connection, lock_id: str, order_id: str, user_id: str, now: datetime) -> dict | None:
    """Return seats from a paid order to inventory during a refund.

    A successful payment converts ACTIVE/LOCKED seats to SOLD and clears
    showtime_seats.lock_id, so the normal temporary-lock release path cannot
    find them. Refunds must therefore release the SOLD rows by order_id.
    """
    lock = connection.execute(
        "SELECT * FROM seat_locks WHERE id=? AND user_id=?",
        (lock_id, user_id),
    ).fetchone()
    if not lock:
        return None

    if lock['status'] == 'CONVERTED':
        connection.execute(
            "UPDATE showtime_seats SET status='AVAILABLE', order_id=NULL, lock_id=NULL, "
            "version=version+1, updated_at=? "
            "WHERE showtime_id=? AND status='SOLD' AND order_id=?",
            (iso_utc(now), lock['showtime_id'], order_id),
        )
        connection.execute(
            "UPDATE seat_locks SET status='RELEASED', released_at=?, release_reason='REFUND' WHERE id=?",
            (iso_utc(now), lock_id),
        )

    lock = connection.execute("SELECT * FROM seat_locks WHERE id=?", (lock_id,)).fetchone()
    return _lock_json(connection, lock, now)

def release_user_locks(connection: sqlite3.Connection, user_id: str, now: datetime, reason: str = 'LOGOUT') -> int:
    _expire(connection, now)
    locks = connection.execute("SELECT id FROM seat_locks WHERE user_id=? AND status='ACTIVE'", (user_id,)).fetchall()
    for lock in locks: _release(connection, lock['id'], now, 'RELEASED', reason)
    return len(locks)

def invalidate_showtime_locks(connection: sqlite3.Connection, showtime_id: str, now: datetime) -> int:
    """Called by showtime cancellation/stop-sale workflows; SOLD rows are never reopened."""
    locks = connection.execute("SELECT id FROM seat_locks WHERE showtime_id=? AND status='ACTIVE'", (showtime_id,)).fetchall()
    for lock in locks: _release(connection, lock['id'], now, 'INVALIDATED', 'SHOWTIME_STOPPED')
    return len(locks)

def convert_lock_to_sold(connection: sqlite3.Connection, lock_id: str, now: datetime, order_id: str) -> bool:
    """Shared 004 hand-off: conversion is final and cleanup can never reopen SOLD."""
    lock = connection.execute("SELECT * FROM seat_locks WHERE id=?", (lock_id,)).fetchone()
    if not lock or lock['status'] != 'ACTIVE' or lock['expires_at'] <= iso_utc(now):
        return False
    changed = connection.execute(
        "UPDATE showtime_seats SET status='SOLD', order_id=?, lock_id=NULL, version=version+1, updated_at=? "
        "WHERE showtime_id=? AND lock_id=? AND status='LOCKED'",
        (order_id, iso_utc(now), lock['showtime_id'], lock_id),
    ).rowcount
    if changed != len(connection.execute("SELECT 1 FROM seat_lock_items WHERE lock_id=?", (lock_id,)).fetchall()):
        raise RuntimeError("could not convert every locked seat")
    connection.execute("UPDATE seat_locks SET status='CONVERTED', released_at=?, release_reason='ORDER_CREATED' WHERE id=?", (iso_utc(now), lock_id))
    return True
