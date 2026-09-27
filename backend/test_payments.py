import tempfile, unittest
from datetime import datetime, timezone
from pathlib import Path
from app import create_app

NOW=datetime(2026,9,25,2,tzinfo=timezone.utc)
class PaymentApiTest(unittest.TestCase):
 def setUp(self): self.tmp=tempfile.TemporaryDirectory();self.app=create_app(Path(self.tmp.name)/'db.sqlite',{'TESTING':True,'NOW_PROVIDER':lambda:NOW});self.c=self.app.test_client();self.t=self.c.post('/auth/register',json={'username':'Test','email':'pay@example.com','password':'password123','confirmPassword':'password123'}).get_json()['token']
 def tearDown(self):self.tmp.cleanup()
 def h(self,k=None):return {'Authorization':'Bearer '+self.t,**({'Idempotency-Key':k} if k else {})}
 def test_order_payment_callback_is_authoritative_and_idempotent(self):
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.h('payment-lock-00001'),json={'seatIds':['A-02'],'observedInventoryVersion':snap['inventoryVersion']}).get_json()
  order=self.c.post('/v1/orders',headers=self.h('create-order-0001'),json={'lockId':lock['id'],'acceptedQuoteVersion':1}).get_json();self.assertEqual('PENDING_PAYMENT',order['status'])
  attempt=self.c.post('/v1/orders/'+order['id']+'/payment-attempts',headers=self.h('payment-try-00001'),json={'method':'ALIPAY_SIMULATED'}).get_json();self.assertEqual('PROCESSING',attempt['status'])
  callback={'transactionRef':attempt['transactionRef'],'status':'SUCCEEDED'};self.assertEqual('PAID',self.c.post('/v1/payment-provider/webhooks',json=callback).get_json()['status']);self.assertEqual('PAID',self.c.post('/v1/payment-provider/webhooks',json=callback).get_json()['status'])
  self.assertEqual(1,len(self.c.get('/v1/orders',headers=self.h()).get_json()['orders']))
  self.assertEqual('READY',self.c.get('/v1/orders/'+order['id']+'/ticket',headers=self.h()).get_json()['status'])

 def test_payment_confirmation_is_explicit_and_idempotent(self):
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.h('confirm-lock-0001'),json={'seatIds':['A-04'],'observedInventoryVersion':snap['inventoryVersion']}).get_json()
  order=self.c.post('/v1/orders',headers=self.h('confirm-order-0001'),json={'lockId':lock['id'],'acceptedQuoteVersion':1}).get_json();attempt=self.c.post('/v1/orders/'+order['id']+'/payment-attempts',headers=self.h('confirm-pay-0001'),json={'method':'WECHAT_SIMULATED'}).get_json()
  result=self.c.post('/v1/orders/'+order['id']+'/payment-confirmations',headers=self.h(),json={'paymentAttemptId':attempt['id']}).get_json()
  self.assertEqual({'orderStatus':'PAID','paymentStatus':'SUCCEEDED','ticketReady':True}, {k:result[k] for k in ('orderStatus','paymentStatus','ticketReady')})
  again=self.c.post('/v1/orders/'+order['id']+'/payment-confirmations',headers=self.h(),json={'paymentAttemptId':attempt['id']}).get_json();self.assertEqual(result['ticketId'],again['ticketId'])
  tickets=self.c.get('/v1/tickets',headers=self.h()).get_json()['tickets'];self.assertEqual(1,len(tickets));self.assertEqual(order['id'],tickets[0]['orderId']);self.assertTrue(tickets[0]['seats'])

 def test_refund_paid_order_releases_seats_and_hides_ticket(self):
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.h('refund-lock-0001'),json={'seatIds':['A-06'],'observedInventoryVersion':snap['inventoryVersion']}).get_json()
  order=self.c.post('/v1/orders',headers=self.h('refund-order-0001'),json={'lockId':lock['id'],'acceptedQuoteVersion':1}).get_json();attempt=self.c.post('/v1/orders/'+order['id']+'/payment-attempts',headers=self.h('refund-pay-00001'),json={'method':'WECHAT_SIMULATED'}).get_json();self.assertIn('transactionRef',attempt,attempt);callback={'transactionRef':attempt['transactionRef'],'status':'SUCCEEDED'}
  self.c.post('/v1/payment-provider/webhooks',json=callback)
  before=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();before_seat=next(p for r in before['rows'] for p in r['positions'] if p['seatId']=='A-06');self.assertEqual('SOLD',before_seat['inventoryStatus'])
  refunded=self.c.post('/v1/orders/'+order['id']+'/refund',headers=self.h())
  self.assertEqual(200,refunded.status_code);self.assertEqual('REFUNDED',refunded.get_json()['status'])
  after=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();after_seat=next(p for r in after['rows'] for p in r['positions'] if p['seatId']=='A-06');self.assertEqual('AVAILABLE',after_seat['inventoryStatus'])
  self.assertEqual([],self.c.get('/v1/tickets',headers=self.h()).get_json()['tickets'])

 def test_payment_confirmation_rejects_non_owner(self):
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.h('owner-lock-000001'),json={'seatIds':['A-04'],'observedInventoryVersion':snap['inventoryVersion']}).get_json();order=self.c.post('/v1/orders',headers=self.h('owner-order-000001'),json={'lockId':lock['id'],'acceptedQuoteVersion':1}).get_json();attempt=self.c.post('/v1/orders/'+order['id']+'/payment-attempts',headers=self.h('owner-pay-000001'),json={'method':'ALIPAY_SIMULATED'}).get_json()
  other=self.c.post('/auth/register',json={'username':'Other','email':'other-pay@example.com','password':'password123','confirmPassword':'password123'}).get_json()['token']
  response=self.c.post('/v1/orders/'+order['id']+'/payment-confirmations',headers={'Authorization':'Bearer '+other},json={'paymentAttemptId':attempt['id']});self.assertEqual(404,response.status_code)
 def test_hundred_duplicate_webhooks_are_idempotent(self):
  snap=self.c.get('/v1/showtimes/st_sh_001/seats',headers=self.h()).get_json();lock=self.c.post('/v1/showtimes/st_sh_001/seat-locks',headers=self.h('payment-lock-00002'),json={'seatIds':['A-03'],'observedInventoryVersion':snap['inventoryVersion']}).get_json();order=self.c.post('/v1/orders',headers=self.h('create-order-0002'),json={'lockId':lock['id'],'acceptedQuoteVersion':1}).get_json();attempt=self.c.post('/v1/orders/'+order['id']+'/payment-attempts',headers=self.h('payment-try-00002'),json={'method':'WECHAT_SIMULATED'}).get_json();callback={'eventId':'provider-event-00000001','transactionRef':attempt['transactionRef'],'status':'SUCCEEDED'}
  for _ in range(100): self.assertEqual('PAID',self.c.post('/v1/payment-provider/webhooks',json=callback).get_json()['status'])
if __name__=='__main__':unittest.main()
