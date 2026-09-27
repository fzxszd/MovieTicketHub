import tempfile
import unittest
from pathlib import Path

from app import connect, create_app
from auth import token_digest, to_timestamp, utc_now


class SyncApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_directory = tempfile.TemporaryDirectory()
        self.database = Path(self.temp_directory.name) / "test.db"
        self.app = create_app(self.database, {"TESTING": True})
        self.client = self.app.test_client()
        self.alice = self.register("alice@example.com", "Alice")
        self.bob = self.register("bob@example.com", "Bob")

    def tearDown(self):
        self.temp_directory.cleanup()

    def register(self, email, username):
        response = self.client.post("/auth/register", json={
            "username": username, "email": email, "password": "password123",
            "confirmPassword": "password123",
        })
        return response.get_json()["token"]

    @staticmethod
    def headers(token):
        return {"Authorization": f"Bearer {token}"}

    def test_health(self):
        self.assertEqual({"status": "ok"}, self.client.get("/health").get_json())

    def test_sync_requires_valid_session(self):
        self.assertEqual(401, self.client.get("/sync/pull").status_code)
        self.assertEqual(401, self.client.post("/sync/push", json={}).status_code)
        with connect(str(self.database)) as connection:
            connection.execute("UPDATE auth_sessions SET revoked_at=? WHERE token_hash=?",
                               (to_timestamp(utc_now()), token_digest(self.alice)))
        self.assertEqual(401, self.client.get("/sync/pull", headers=self.headers(self.alice)).status_code)

    def test_identity_fields_are_rejected(self):
        for payload in ({"email": "bob@example.com"}, {"password": "secret"}, {"data": {}}):
            self.assertEqual(400, self.client.post("/sync/push", json=payload,
                                                  headers=self.headers(self.alice)).status_code)

    def test_each_token_only_reads_and_writes_its_own_data(self):
        alice_data = sync_data(name="Alice", favorites=[{"id": 123, "title": "测试电影"}])
        bob_data = sync_data(name="Bob", favorites=[{"id": 456}])
        self.assertEqual(200, self.client.post("/sync/push", json=alice_data,
                                              headers=self.headers(self.alice)).status_code)
        self.assertEqual(200, self.client.post("/sync/push", json=bob_data,
                                              headers=self.headers(self.bob)).status_code)
        alice_pull = self.client.get("/sync/pull", headers=self.headers(self.alice)).get_json()
        bob_pull = self.client.get("/sync/pull", headers=self.headers(self.bob)).get_json()
        self.assertEqual(123, alice_pull["favorites"][0]["id"])
        self.assertEqual(456, bob_pull["favorites"][0]["id"])
        self.assertNotIn("email", alice_pull)
        self.assertNotIn("password", alice_pull)

    def test_comments_are_shared_and_likes_are_aggregated(self):
        comment = {"id": 42, "movieId": 7, "userEmail": "alice@example.com",
                   "userName": "Alice", "content": "很好看", "timestamp": 1000,
                   "parentId": None, "likes": 0}
        self.client.post("/sync/push", json=sync_data(name="Alice", comments=[comment]),
                         headers=self.headers(self.alice))
        self.client.post("/sync/push", json=sync_data(name="Bob", likedCommentIds=[42]),
                         headers=self.headers(self.bob))
        pulled = self.client.get("/sync/pull", headers=self.headers(self.bob)).get_json()
        self.assertEqual([dict(comment, likes=1)], pulled["globalComments"])


def sync_data(**overrides):
    data = {"name": "Test User", "avatarUri": None, "favorites": [], "orders": [],
            "ratings": [], "comments": [], "globalComments": [], "likedCommentIds": []}
    data.update(overrides)
    return data


if __name__ == "__main__":
    unittest.main()
