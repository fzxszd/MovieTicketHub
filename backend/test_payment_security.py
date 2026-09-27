import hashlib
import hmac
import json
import os
import tempfile
import unittest
from pathlib import Path
from app import create_app

class PaymentSecurityTest(unittest.TestCase):
 def test_webhook_signature_is_verified_when_configured(self):
  old=os.environ.get('PAYMENT_WEBHOOK_SECRET');os.environ['PAYMENT_WEBHOOK_SECRET']='test-secret'
  try:
   tmp=tempfile.TemporaryDirectory();app=create_app(Path(tmp.name)/'db.sqlite',{'TESTING':True});client=app.test_client();body=json.dumps({'transactionRef':'missing','status':'SUCCEEDED'}).encode();sig=hmac.new(b'test-secret',body,hashlib.sha256).hexdigest()
   self.assertEqual(401,client.post('/v1/payment-provider/webhooks',data=body,content_type='application/json').status_code);self.assertEqual(404,client.post('/v1/payment-provider/webhooks',data=body,content_type='application/json',headers={'X-Payment-Signature':sig}).status_code);tmp.cleanup()
  finally:
   if old is None:os.environ.pop('PAYMENT_WEBHOOK_SECRET',None)
   else:os.environ['PAYMENT_WEBHOOK_SECRET']=old
if __name__=='__main__':unittest.main()
