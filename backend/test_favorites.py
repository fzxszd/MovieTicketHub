import tempfile
import unittest
from pathlib import Path

from app import create_app, connect


class FavoritesApiTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.db = Path(self.tmp.name) / "favorites.db"
        self.app = create_app(self.db, {"TESTING": True}); self.client = self.app.test_client()
        self.a = self.register("a@example.com", "Alice"); self.b = self.register("b@example.com", "Bob")

    def tearDown(self): self.tmp.cleanup()

    def register(self, email, username):
        return self.client.post("/auth/register", json={"username": username, "email": email, "password": "password123", "confirmPassword": "password123"}).get_json()["token"]

    def h(self, token=None, key=None):
        value = {"Authorization": f"Bearer {token or self.a}"}
        if key: value["Idempotency-Key"] = key
        return value

    def put(self, movie=123, target=True, key="favorite-operation-0001", token=None, seq=1):
        return self.client.put(f"/v1/favorites/{movie}", headers=self.h(token or self.a, key), json={"targetState": target, "deviceId": "device-000000000001", "localSequence": seq})

    def test_requires_auth_and_rejects_identity_payload(self):
        self.assertEqual(401, self.client.get("/v1/favorites").status_code)
        self.assertEqual(400, self.client.put("/v1/favorites/1", headers={"Idempotency-Key": "1234567890123456"}, json={"targetState": True, "deviceId": "device-000000000001", "localSequence": 1, "email": "b@example.com"}).status_code)

    def test_explicit_set_is_idempotent_and_tombstone_is_visible(self):
        first = self.put().get_json(); self.assertEqual("ACCEPTED", first["result"])
        replay = self.put().get_json(); self.assertEqual("ALREADY_PROCESSED", replay["result"])
        self.assertEqual(first["serverRevision"], replay["serverRevision"])
        removed = self.put(target=False, key="favorite-operation-0002", seq=2).get_json(); self.assertFalse(removed["authoritativeState"])
        changes = self.client.get("/v1/favorites?afterRevision=0", headers=self.h()).get_json()["changes"]
        self.assertEqual([True, False], [x["isFavorite"] for x in changes])

    def test_legacy_favorite_is_repaired_with_catalog_snapshot(self):
        self.put(movie=123)
        with connect(str(self.db)) as connection:
            connection.execute("INSERT INTO movie_catalog(movie_id,title,genres_json,themes_json,language,people_json,popularity,release_date,availability,catalog_version,updated_at,poster_url) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", (123,"Legacy movie","[]","[]","zh-CN","[]",1,None,"AVAILABLE","maoyan-list-v1","now","https://example.test/legacy.jpg"))
        revision = self.client.get("/v1/favorites", headers=self.h()).get_json()["serverRevision"]
        repaired = self.client.get(f"/v1/favorites?afterRevision={revision}", headers=self.h()).get_json()["changes"]
        self.assertEqual("https://example.test/legacy.jpg", repaired[0]["movie"]["posterUrl"])

    def test_accounts_are_isolated(self):
        self.put(); self.assertEqual([], self.client.get("/v1/favorites", headers=self.h(self.b)).get_json()["changes"])

    def test_server_stores_device_hash_only_and_operation_key_cannot_change_movie(self):
        self.assertEqual(200, self.put().status_code)
        conflict = self.put(movie=124, key="favorite-operation-0001")
        self.assertEqual(409, conflict.status_code)
        with connect(str(self.db)) as connection:
            row = connection.execute("SELECT device_id_hash FROM favorite_operations").fetchone()
        self.assertNotEqual("device-000000000001", row[0]); self.assertEqual(64, len(row[0]))


if __name__ == "__main__": unittest.main()
