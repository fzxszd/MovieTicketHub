import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from app import create_app

NOW=datetime(2026,9,25,2,tzinfo=timezone.utc)
class OrderApiTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.app=create_app(Path(self.tmp.name)/'db.sqlite',{'TESTING':True,'NOW_PROVIDER':lambda:NOW});self.c=self.app.test_client()
  self.t=self.c.post('/auth/register',json={'username':'Order User','email':'order@example.com','password':'password123','confirmPassword':'password123'}).get_json()['token'];self.h={'Authorization':'Bearer '+self.t}
 def tearDown(self):self.tmp.cleanup()
 def test_quote_create_idempotent_and_detail(self):
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers={**self.h,'Idempotency-Key':'order-lock-0000001'},json={'seatIds':['A-02'],'observedInventoryVersion':snap['inventoryVersion']}).get_json()
  quote=self.c.get('/v1/seat-locks/'+lock['id']+'/order-quote',headers=self.h).get_json();self.assertEqual(quote['total']['amountMinor'],quote['subtotal']['amountMinor']+quote['fees']['amountMinor'])
  headers={**self.h,'Idempotency-Key':'order-create-00001'};first=self.c.post('/v1/orders',headers=headers,json={'lockId':lock['id'],'acceptedQuoteVersion':quote['quoteVersion']});second=self.c.post('/v1/orders',headers=headers,json={'lockId':lock['id'],'acceptedQuoteVersion':quote['quoteVersion']});self.assertEqual(first.status_code,201);self.assertEqual(second.status_code,200);self.assertEqual(first.get_json()['id'],second.get_json()['id'])
  detail=self.c.get('/v1/orders/'+first.get_json()['id'],headers=self.h).get_json();self.assertEqual(detail['items'][0]['seatId'],'A-02')
 def test_invalid_quote_and_unauthenticated(self):
  self.assertEqual(self.c.get('/v1/orders').status_code,401)
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers={**self.h,'Idempotency-Key':'order-lock-0000002'},json={'seatIds':['A-03'],'observedInventoryVersion':snap['inventoryVersion']}).get_json();r=self.c.post('/v1/orders',headers={**self.h,'Idempotency-Key':'order-create-00002'},json={'lockId':lock['id'],'acceptedQuoteVersion':99});self.assertEqual(r.status_code,409);self.assertEqual(r.get_json()['code'],'AMOUNT_CHANGED')
if __name__=='__main__':unittest.main()
