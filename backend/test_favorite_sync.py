import tempfile
import unittest
import time
from pathlib import Path
from app import create_app


class FavoriteSyncTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.app = create_app(Path(self.tmp.name) / "sync.db", {"TESTING": True}); self.client = self.app.test_client()
        self.token = self.client.post("/auth/register", json={"username":"Sync User","email":"sync@example.com","password":"password123","confirmPassword":"password123"}).get_json()["token"]

    def tearDown(self): self.tmp.cleanup()
    def h(self): return {"Authorization": f"Bearer {self.token}"}

    def test_ordered_batch_duplicate_and_incremental_pull(self):
        body = {"deviceId":"device-000000000001","sinceRevision":0,"operations":[
            {"clientOperationId":"11111111-1111-1111-1111-111111111111","movieId":1,"targetState":True,"localSequence":1},
            {"clientOperationId":"22222222-2222-2222-2222-222222222222","movieId":1,"targetState":False,"localSequence":2}]}
        response = self.client.post("/v1/favorites/sync", headers=self.h(), json=body)
        self.assertEqual(200, response.status_code); self.assertEqual([1,2], [x["serverRevision"] for x in response.get_json()["operationResults"]])
        replay = self.client.post("/v1/favorites/sync", headers=self.h(), json={**body, "sinceRevision":2}).get_json()
        self.assertTrue(all(x["result"] == "ALREADY_PROCESSED" for x in replay["operationResults"]))
        self.assertEqual([], replay["changes"])

    def test_batch_rejects_non_monotonic_sequence_and_overflow(self):
        base = {"deviceId":"device-000000000001","sinceRevision":0}
        bad = {**base, "operations":[{"clientOperationId":"11111111-1111-1111-1111-111111111111","movieId":1,"targetState":True,"localSequence":2},{"clientOperationId":"22222222-2222-2222-2222-222222222222","movieId":2,"targetState":True,"localSequence":1}]}
        self.assertEqual(400, self.client.post("/v1/favorites/sync", headers=self.h(), json=bad).status_code)
        too_many = {**base, "operations":[{"clientOperationId":f"{i:032d}","movieId":i+1,"targetState":True,"localSequence":i+1} for i in range(101)]}
        self.assertEqual(400, self.client.post("/v1/favorites/sync", headers=self.h(), json=too_many).status_code)

    def test_repeated_sync_is_fast_and_idempotent(self):
        body = {"deviceId":"device-000000000001","sinceRevision":0,"operations":[]}
        durations=[]
        for _ in range(20):
            started=time.perf_counter(); self.assertEqual(200,self.client.post("/v1/favorites/sync",headers=self.h(),json=body).status_code); durations.append(time.perf_counter()-started)
        durations.sort(); self.assertLess(durations[int(len(durations)*.95)-1], 2.0)


if __name__ == "__main__": unittest.main()
