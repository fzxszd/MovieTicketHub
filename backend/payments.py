"""Server-authoritative simulated payment state transitions."""
import uuid
from showtimes import iso_utc
from seats import convert_lock_to_sold, release_lock

TERMINAL = {'PAID','PAYMENT_FAILED','CANCELLED','EXPIRED'}

def start_attempt(connection, order_id, user_id, method, key, now):
    order=connection.execute('SELECT * FROM orders WHERE id=? AND user_id=?',(order_id,user_id)).fetchone()
    if not order:return None
    existing=connection.execute('SELECT * FROM payment_attempts WHERE order_id=? AND idempotency_key=?',(order_id,key)).fetchone()
    if existing:return existing
    if order['status'] not in ('PENDING_PAYMENT','PAYMENT_FAILED') or order['payment_deadline']<=iso_utc(now):return False
    aid=str(uuid.uuid4()); ref='sim_'+str(uuid.uuid4())
    connection.execute('INSERT INTO payment_attempts VALUES (?,?,?,?,?,?,?)',(aid,order_id,key,'PROCESSING',method,ref,iso_utc(now)))
    connection.execute("UPDATE orders SET status='PAYMENT_PROCESSING' WHERE id=?",(order_id,))
    connection.execute('INSERT INTO status_events VALUES (?,?,?,?,?,?,?,?)',(str(uuid.uuid4()),'ORDER',order_id,order['status'],'PAYMENT_PROCESSING','PAYMENT_STARTED',str(uuid.uuid4()),iso_utc(now)))
    return connection.execute('SELECT * FROM payment_attempts WHERE id=?',(aid,)).fetchone()

def apply_provider_result(connection, ref, status, now):
    attempt=connection.execute('SELECT * FROM payment_attempts WHERE transaction_ref=?',(ref,)).fetchone()
    if not attempt:return None
    order=connection.execute('SELECT * FROM orders WHERE id=?',(attempt['order_id'],)).fetchone()
    # Terminal state wins over delayed, duplicated or out-of-order provider events.
    if order['status'] in TERMINAL:return order
    if status=='SUCCEEDED':
        if not convert_lock_to_sold(connection,order['seat_lock_id'],now,order['id']):return False
        connection.execute("UPDATE orders SET status='PAID',paid_at=? WHERE id=?",(iso_utc(now),order['id']))
        connection.execute('INSERT INTO status_events VALUES (?,?,?,?,?,?,?,?)',(str(uuid.uuid4()),'ORDER',order['id'],'PAYMENT_PROCESSING','PAID','PROVIDER_SUCCEEDED',str(uuid.uuid4()),iso_utc(now)))
        connection.execute('INSERT OR IGNORE INTO tickets VALUES (?,?,?,?,?)',(str(uuid.uuid4()),order['id'],'READY','ticket-'+order['id'],iso_utc(now)))
        final='SUCCEEDED'
    else:
        final={'FAILED':'FAILED','CANCELLED':'CANCELLED','UNKNOWN':'UNKNOWN'}.get(status,'PROCESSING')
        if final in ('FAILED','CANCELLED'):
            next_status='PAYMENT_'+final if final=='FAILED' else final
            connection.execute('UPDATE orders SET status=? WHERE id=?',(next_status,order['id']))
            connection.execute('INSERT INTO status_events VALUES (?,?,?,?,?,?,?,?)',(str(uuid.uuid4()),'ORDER',order['id'],'PAYMENT_PROCESSING',next_status,'PROVIDER_'+final,str(uuid.uuid4()),iso_utc(now)))
    connection.execute('UPDATE payment_attempts SET status=? WHERE id=?',(final,attempt['id']))
    return connection.execute('SELECT * FROM orders WHERE id=?',(order['id'],)).fetchone()

def expire_pending_orders(connection, now):
    rows=connection.execute("SELECT * FROM orders WHERE status IN ('PENDING_PAYMENT','PAYMENT_PROCESSING') AND payment_deadline<=?",(iso_utc(now),)).fetchall()
    for order in rows:
        connection.execute("UPDATE orders SET status='EXPIRED' WHERE id=?",(order['id'],))
        connection.execute("UPDATE payment_attempts SET status='CANCELLED' WHERE order_id=? AND status='PROCESSING'",(order['id'],))
        release_lock(connection, order['seat_lock_id'], order['user_id'], now)
        connection.execute('INSERT INTO status_events VALUES (?,?,?,?,?,?,?,?)',(str(uuid.uuid4()),'ORDER',order['id'],order['status'],'EXPIRED','DEADLINE',str(uuid.uuid4()),iso_utc(now)))
    return len(rows)
