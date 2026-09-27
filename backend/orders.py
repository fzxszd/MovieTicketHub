"""Authoritative order quotation from an active seat lock."""
from __future__ import annotations
import sqlite3
import uuid
from datetime import datetime
from seats import get_lock
from showtimes import joined_showtimes, iso_utc

def initialize_order_schema(connection: sqlite3.Connection) -> None:
    connection.executescript("""
    CREATE TABLE IF NOT EXISTS orders(id TEXT PRIMARY KEY,user_id TEXT NOT NULL,seat_lock_id TEXT UNIQUE NOT NULL,status TEXT NOT NULL,total_minor INTEGER NOT NULL,currency TEXT NOT NULL,quote_version INTEGER NOT NULL,created_at TEXT NOT NULL,payment_deadline TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS order_items(order_id TEXT NOT NULL,seat_id TEXT NOT NULL,row_label TEXT NOT NULL,seat_label TEXT NOT NULL,unit_price_minor INTEGER NOT NULL,currency TEXT NOT NULL,PRIMARY KEY(order_id,seat_id));
    CREATE TABLE IF NOT EXISTS payment_attempts(id TEXT PRIMARY KEY,order_id TEXT NOT NULL,idempotency_key TEXT NOT NULL,status TEXT NOT NULL,method TEXT NOT NULL,transaction_ref TEXT UNIQUE,created_at TEXT NOT NULL,UNIQUE(order_id,idempotency_key));
    CREATE TABLE IF NOT EXISTS payment_events(id TEXT PRIMARY KEY,transaction_ref TEXT NOT NULL,status TEXT NOT NULL,received_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS tickets(id TEXT PRIMARY KEY,order_id TEXT UNIQUE NOT NULL,status TEXT NOT NULL,credential TEXT,issued_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS status_events(id TEXT PRIMARY KEY,entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,from_status TEXT,to_status TEXT NOT NULL,reason TEXT,correlation_id TEXT,created_at TEXT NOT NULL);
    """)
    columns = {row[1] for row in connection.execute('PRAGMA table_info(orders)')}
    if 'paid_at' not in columns:
        connection.execute('ALTER TABLE orders ADD COLUMN paid_at TEXT')

def quote_for_lock(connection: sqlite3.Connection, lock_id: str, user_id: str, now: datetime) -> dict | None:
    lock = get_lock(connection, lock_id, user_id, now)
    if lock is None or lock['status'] != 'ACTIVE': return None
    show = joined_showtimes(connection, showtime_id=lock['showtimeId'])
    if not show: return None
    row=show[0]; items=lock['items']; subtotal=lock['totalPrice']['amountMinor']; currency=lock['totalPrice']['currency']; fee=0
    return {'lockId':lock_id,'quoteVersion':1,'movie':{'id':row['movie_id'],'title':'Movie '+str(row['movie_id']),'posterUrl':None},'cinema':{'id':row['cinema_id'],'name':row['cinema_name']},'auditorium':{'id':row['auditorium_id'],'name':row['auditorium_name']},'showtime':{'id':row['id'],'startsAt':row['starts_at'],'timeZone':row['time_zone']},'items':[{'seatId':i['seatId'],'rowLabel':i['rowLabel'],'seatLabel':i['seatLabel'],'priceZoneId':i['priceZoneId'],'unitPrice':i['unitPrice']} for i in items],'subtotal':{'amountMinor':subtotal,'currency':currency},'fees':{'amountMinor':fee,'currency':currency},'total':{'amountMinor':subtotal+fee,'currency':currency},'expiresAt':lock['expiresAt'],'serverTime':iso_utc(now)}

def create_order(connection, lock_id, user_id, quote_version, now):
    existing=connection.execute('SELECT * FROM orders WHERE seat_lock_id=?',(lock_id,)).fetchone()
    if existing:return 200,order_json(connection, existing)
    quote=quote_for_lock(connection,lock_id,user_id,now)
    if quote is None:return 409,{'code':'LOCK_INVALID','message':'Seat lock is invalid'}
    if quote_version!=quote['quoteVersion']:return 409,{'code':'AMOUNT_CHANGED','message':'Quote changed','latestQuote':quote}
    oid=str(uuid.uuid4()); connection.execute('INSERT INTO orders(id,user_id,seat_lock_id,status,total_minor,currency,quote_version,created_at,payment_deadline,paid_at) VALUES (?,?,?,?,?,?,?,?,?,NULL)',(oid,user_id,lock_id,'PENDING_PAYMENT',quote['total']['amountMinor'],quote['total']['currency'],quote_version,iso_utc(now),quote['expiresAt']))
    connection.execute('INSERT INTO status_events VALUES (?,?,?,?,?,?,?,?)',(str(uuid.uuid4()),'ORDER',oid,None,'PENDING_PAYMENT','CREATED',str(uuid.uuid4()),iso_utc(now)))
    for item in quote['items']:connection.execute('INSERT INTO order_items VALUES (?,?,?,?,?,?)',(oid,item['seatId'],item['rowLabel'],item['seatLabel'],item['unitPrice']['amountMinor'],item['unitPrice']['currency']))
    return 201,order_json(connection, connection.execute('SELECT * FROM orders WHERE id=?',(oid,)).fetchone())

def order_json(connection, order):
    lock = connection.execute('SELECT showtime_id FROM seat_locks WHERE id=?', (order['seat_lock_id'],)).fetchone()
    show = joined_showtimes(connection, showtime_id=lock['showtime_id']) if lock else []
    row = show[0] if show else None
    items = connection.execute('SELECT * FROM order_items WHERE order_id=? ORDER BY seat_id', (order['id'],)).fetchall()
    lock_items = {r['seat_id']: r['price_zone_id'] for r in connection.execute('SELECT seat_id,price_zone_id FROM seat_lock_items WHERE lock_id=?',(order['seat_lock_id'],)).fetchall()}
    ticket = connection.execute('SELECT * FROM tickets WHERE order_id=?', (order['id'],)).fetchone()
    allowed = ['PAY'] if order['status'] == 'PENDING_PAYMENT' else (['RETRY_PAYMENT'] if order['status'] == 'PAYMENT_FAILED' else [])
    if order['status'] == 'PAID' and ticket: allowed.append('VIEW_TICKET')
    return {'id':order['id'],'status':order['status'],'total':{'amountMinor':order['total_minor'],'currency':order['currency']},'createdAt':order['created_at'],'paymentDeadline':order['payment_deadline'],'quoteVersion':order['quote_version'],'allowedActions':allowed,
      'movie':({'id':row['movie_id'],'title':'Movie '+str(row['movie_id']),'posterUrl':None} if row else None),
      'cinema':({'id':row['cinema_id'],'name':row['cinema_name']} if row else None),
      'auditorium':({'id':row['auditorium_id'],'name':row['auditorium_name']} if row else None),
      'showtime':({'id':row['id'],'startsAt':row['starts_at'],'timeZone':row['time_zone']} if row else None),
      'items':[{'seatId':i['seat_id'],'rowLabel':i['row_label'],'seatLabel':i['seat_label'],'priceZoneId':lock_items.get(i['seat_id']),'unitPrice':{'amountMinor':i['unit_price_minor'],'currency':i['currency']}} for i in items],
      'paidAt':order['paid_at'] if 'paid_at' in order.keys() else None,'ticket':({'id':ticket['id'],'orderId':ticket['order_id'],'status':ticket['status'],'credential':ticket['credential'],'issuedAt':ticket['issued_at']} if ticket else None)}

def refund_order(connection: sqlite3.Connection, order_id: str, user_id: str, now: datetime) -> sqlite3.Row | None:
    order = connection.execute('SELECT * FROM orders WHERE id=? AND user_id=?', (order_id, user_id)).fetchone()
    if not order or order['status'] != 'PAID':
        return None
    connection.execute('UPDATE orders SET status=?, paid_at=NULL WHERE id=?', ('REFUNDED', order_id))
    connection.execute('UPDATE tickets SET status=? WHERE order_id=?', ('REFUNDED', order_id))
    connection.execute('INSERT INTO status_events VALUES (?,?,?,?,?,?,?,?)', (str(uuid.uuid4()), 'ORDER', order_id, 'PAID', 'REFUNDED', 'USER_REFUND', str(uuid.uuid4()), iso_utc(now)))
    return connection.execute('SELECT * FROM orders WHERE id=?', (order_id,)).fetchone()
