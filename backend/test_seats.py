import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from app import create_app, connect
from seats import convert_lock_to_sold

NOW = datetime(2026, 9, 25, 2, 0, tzinfo=timezone.utc)

class SeatApiTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.app=create_app(Path(self.tmp.name)/'seats.db',{'TESTING':True,'NOW_PROVIDER':lambda:NOW}); self.client=self.app.test_client()
    def tearDown(self): self.tmp.cleanup()
    def account(self,email):
        body={'username':'Tester','email':email,'password':'password123','confirmPassword':'password123'}
        return self.client.post('/auth/register',json=body).get_json()['token']
    def headers(self,token,key=None):
        h={'Authorization':'Bearer '+token}
        if key: h['Idempotency-Key']=key
        return h
    def test_authenticated_snapshot_and_atomic_idempotent_lock(self):
        token=self.account('seat@example.com'); snapshot=self.client.get('/v1/showtimes/st_sh_001/seats',headers=self.headers(token))
        self.assertEqual(200,snapshot.status_code); version=snapshot.get_json()['inventoryVersion']
        payload={'seatIds':['A-02','A-03'],'observedInventoryVersion':version}; first=self.client.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.headers(token,'seat-lock-test-0001'),json=payload)
        self.assertEqual(201,first.status_code); lock=first.get_json(); self.assertEqual('ACTIVE',lock['status']); self.assertEqual(2,len(lock['items']))
        repeated=self.client.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.headers(token,'seat-lock-test-0001'),json=payload)
        self.assertEqual(200,repeated.status_code); self.assertEqual(lock['id'],repeated.get_json()['id']); self.assertEqual(lock['expiresAt'],repeated.get_json()['expiresAt'])
    def test_conflict_is_all_or_nothing_and_logout_releases(self):
        one,two=self.account('one@example.com'),self.account('two@example.com'); version=self.client.get('/v1/showtimes/st_sh_001/seats',headers=self.headers(one)).get_json()['inventoryVersion']
        self.assertEqual(201,self.client.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.headers(one,'seat-lock-test-0002'),json={'seatIds':['A-02'],'observedInventoryVersion':version}).status_code)
        conflict=self.client.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.headers(two,'seat-lock-test-0003'),json={'seatIds':['A-02','A-03'],'observedInventoryVersion':version})
        self.assertEqual(409,conflict.status_code)
        self.client.post('/auth/logout',headers=self.headers(one))
        retry=self.client.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.headers(two,'seat-lock-test-0004'),json={'seatIds':['A-02'],'observedInventoryVersion':version})
        self.assertEqual(201,retry.status_code)

    def test_layout_expiry_lifecycle_and_fingerprint_recovery(self):
        now = [NOW]
        self.app = create_app(Path(self.tmp.name) / 'lifecycle.db', {'TESTING': True, 'NOW_PROVIDER': lambda: now[0]})
        self.client = self.app.test_client()
        owner, other = self.account('owner@example.com'), self.account('other@example.com')
        layout = self.client.get('/v1/showtimes/st_sh_001/seats', headers=self.headers(owner)).get_json()
        positions = [p for row in layout['rows'] for p in row['positions']]
        self.assertTrue(any(p['positionType'] == 'AISLE' for p in positions))
        self.assertTrue(any(p['positionType'] == 'EMPTY' for p in positions))
        self.assertTrue(any(p['positionType'] == 'COUPLE_LEFT' for p in positions))
        self.assertTrue(any(p['inventoryStatus'] == 'UNAVAILABLE' for p in positions))
        payload = {'seatIds': ['A-02'], 'observedInventoryVersion': layout['inventoryVersion']}
        first = self.client.post('/v1/showtimes/st_sh_001/seat-locks', headers=self.headers(owner, 'seat-lifecycle-0001'), json=payload)
        self.assertEqual(201, first.status_code)
        lock = first.get_json()
        # Same valid intent is recoverable even when a response-loss retry uses a new key.
        recovered = self.client.post('/v1/showtimes/st_sh_001/seat-locks', headers=self.headers(owner, 'seat-lifecycle-0002'), json=payload)
        self.assertEqual(lock['id'], recovered.get_json()['id'])
        now[0] = NOW.replace(minute=10)
        expired = self.client.get(f"/v1/seat-locks/{lock['id']}", headers=self.headers(owner)).get_json()
        self.assertEqual('EXPIRED', expired['status'])
        self.assertEqual(201, self.client.post('/v1/showtimes/st_sh_001/seat-locks', headers=self.headers(other, 'seat-lifecycle-0003'), json=payload).status_code)

    def test_converted_seats_are_never_reopened(self):
        token = self.account('sold@example.com')
        version = self.client.get('/v1/showtimes/st_sh_001/seats', headers=self.headers(token)).get_json()['inventoryVersion']
        lock = self.client.post('/v1/showtimes/st_sh_001/seat-locks', headers=self.headers(token, 'seat-sold-test-0001'), json={'seatIds':['A-03'], 'observedInventoryVersion': version}).get_json()
        with connect(self.app.config['DATABASE']) as connection:
            self.assertTrue(convert_lock_to_sold(connection, lock['id'], NOW, 'order-test-001'))
        self.client.delete(f"/v1/seat-locks/{lock['id']}", headers=self.headers(token))
        snapshot = self.client.get('/v1/showtimes/st_sh_001/seats', headers=self.headers(token)).get_json()
        sold = next(p for r in snapshot['rows'] for p in r['positions'] if p['seatId'] == 'A-03')
        self.assertEqual('SOLD', sold['inventoryStatus'])

    def test_stop_sale_invalidates_active_lock(self):
        from showtimes import set_showtime_sales_status
        token = self.account('stopped@example.com')
        version = self.client.get('/v1/showtimes/st_sh_001/seats', headers=self.headers(token)).get_json()['inventoryVersion']
        created = self.client.post('/v1/showtimes/st_sh_001/seat-locks', headers=self.headers(token, 'seat-stop-test-0001'), json={'seatIds':['A-03'], 'observedInventoryVersion':version})
        self.assertEqual(201, created.status_code)
        with connect(self.app.config['DATABASE']) as connection:
            self.assertTrue(set_showtime_sales_status(connection, 'st_sh_001', 'STOPPED', NOW))
        lock = self.client.get(f"/v1/seat-locks/{created.get_json()['id']}", headers=self.headers(token)).get_json()
        self.assertEqual('INVALIDATED', lock['status'])

if __name__=='__main__': unittest.main()
