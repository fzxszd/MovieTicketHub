import tempfile
import threading
import time
import unittest
from datetime import datetime, timezone
from pathlib import Path
from app import create_app

class SeatConcurrencyTest(unittest.TestCase):
    def test_same_seat_has_at_most_one_active_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            app=create_app(Path(directory)/'concurrency.db',{'TESTING':True,'NOW_PROVIDER':lambda:datetime(2026,9,25,2,0,tzinfo=timezone.utc)}); client=app.test_client()
            def token(email): return client.post('/auth/register',json={'username':'Tester','email':email,'password':'password123','confirmPassword':'password123'}).get_json()['token']
            first,second=token('first@example.com'),token('second@example.com'); results=[]; guard=threading.Lock()
            def lock(token_value,index):
                with app.test_client() as c:
                    response=c.post('/v1/showtimes/st_sh_001/seat-locks',headers={'Authorization':'Bearer '+token_value,'Idempotency-Key':f'concurrent-seat-{index:04d}'},json={'seatIds':['A-02'],'observedInventoryVersion':1})
                    with guard: results.append(response.status_code)
            threads=[threading.Thread(target=lock,args=((first if i%2 else second),i)) for i in range(100)]
            for thread in threads: thread.start()
            for thread in threads: thread.join()
            self.assertEqual(1,results.count(201)); self.assertTrue(all(code in (200,201,409) for code in results))
            timings=[]
            for _ in range(100):
                started=time.perf_counter(); response=client.get('/v1/showtimes/st_sh_001/seats',headers={'Authorization':'Bearer '+first}); timings.append(time.perf_counter()-started)
                self.assertEqual(200,response.status_code)
            p95=sorted(timings)[94]
            self.assertLess(p95, 0.5, f"seat snapshot p95 was {p95:.3f}s")

if __name__=='__main__': unittest.main()
