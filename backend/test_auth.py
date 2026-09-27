import json
import sqlite3
import tempfile
import unittest
from datetime import timedelta
from pathlib import Path

from app import connect, create_app, initialize_database
from auth import parse_timestamp, token_digest, verify_password


class AuthApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_directory = tempfile.TemporaryDirectory()
        self.database = Path(self.temp_directory.name) / "test.db"
        self.app = create_app(self.database, {
            "TESTING": True,
            "SESSION_LIFETIME": timedelta(days=30),
            "FAILURE_LIMIT": 5,
        })
        self.client = self.app.test_client()

    def tearDown(self):
        self.temp_directory.cleanup()

    def register(self, email="alice@example.com", password="password123", username="Alice"):
        return self.client.post("/auth/register", json={
            "username": username, "email": email, "password": password,
            "confirmPassword": password,
        })

    def bearer(self, token):
        return {"Authorization": f"Bearer {token}"}

    def test_register_normalizes_email_hashes_password_and_never_echoes_it(self):
        response = self.register("  Alice@Example.COM ")
        self.assertEqual(201, response.status_code)
        body = response.get_json()
        self.assertEqual("alice@example.com", body["user"]["email"])
        self.assertNotIn("password", json.dumps(body).lower())
        with connect(str(self.database)) as connection:
            row = connection.execute("SELECT * FROM user_accounts").fetchone()
            session = connection.execute("SELECT * FROM auth_sessions").fetchone()
        self.assertNotEqual("password123", row["password_hash"])
        self.assertTrue(verify_password(row["password_hash"], "password123"))
        self.assertEqual(token_digest(body["token"]), session["token_hash"])
        self.assertGreater(parse_timestamp(body["expiresAt"]), parse_timestamp(session["created_at"]))

    def test_registration_validation_and_duplicate_are_explicit(self):
        invalid = self.client.post("/auth/register", json={
            "username": "x", "email": "bad", "password": "short", "confirmPassword": "different"
        })
        self.assertEqual(400, invalid.status_code)
        self.assertEqual({"username", "email", "password", "confirmPassword"},
                         set(invalid.get_json()["fieldErrors"]))
        self.assertEqual(201, self.register("Alice@Example.com").status_code)
        duplicate = self.register("alice@example.COM")
        self.assertEqual(409, duplicate.status_code)
        self.assertEqual("email_exists", duplicate.get_json()["code"])

    def test_login_unknown_and_wrong_password_have_same_response(self):
        self.register()
        unknown = self.client.post("/auth/login", json={"email": "nobody@example.com", "password": "wrongpass"})
        wrong = self.client.post("/auth/login", json={"email": "alice@example.com", "password": "wrongpass"})
        self.assertEqual((401, unknown.get_json()), (wrong.status_code, wrong.get_json()))

    def test_fifth_failure_blocks_and_success_clears_failures(self):
        self.register()
        for _ in range(4):
            self.assertEqual(401, self.client.post("/auth/login", json={
                "email": "alice@example.com", "password": "wrongpass"
            }).status_code)
        blocked = self.client.post("/auth/login", json={
            "email": "alice@example.com", "password": "wrongpass"
        })
        self.assertEqual(429, blocked.status_code)
        self.assertGreater(blocked.get_json()["retryAfterSeconds"], 0)
        still_blocked = self.client.post("/auth/login", json={
            "email": "alice@example.com", "password": "password123"
        })
        self.assertEqual(429, still_blocked.status_code)

    def test_me_rejects_missing_expired_and_revoked_sessions(self):
        registered = self.register().get_json()
        token = registered["token"]
        self.assertEqual(200, self.client.get("/auth/me", headers=self.bearer(token)).status_code)
        self.assertEqual(204, self.client.post("/auth/logout", headers=self.bearer(token)).status_code)
        self.assertEqual(401, self.client.get("/auth/me", headers=self.bearer(token)).status_code)
        second = self.register("bob@example.com", username="Bob").get_json()["token"]
        with connect(str(self.database)) as connection:
            connection.execute(
                "UPDATE auth_sessions SET created_at=?, expires_at=?, last_used_at=? WHERE token_hash=?",
                ("2020-01-01T00:00:00+00:00", "2020-01-02T00:00:00+00:00",
                 "2020-01-01T00:00:00+00:00", token_digest(second)),
            )
        self.assertEqual(401, self.client.get("/auth/me", headers=self.bearer(second)).status_code)


class MigrationTest(unittest.TestCase):
    def setUp(self):
        self.temp_directory = tempfile.TemporaryDirectory()
        self.database = Path(self.temp_directory.name) / "legacy.db"
        connection = sqlite3.connect(self.database)
        connection.execute("CREATE TABLE user_sync_data(email TEXT PRIMARY KEY,payload TEXT NOT NULL,updated_at TEXT NOT NULL)")
        connection.execute("CREATE TABLE global_comments(id INTEGER PRIMARY KEY,payload TEXT NOT NULL,updated_at TEXT NOT NULL)")
        payload = {"email": "Legacy@Example.com", "name": "Legacy", "password": "old-secret",
                   "favorites": [{"id": 1}]}
        connection.execute("INSERT INTO user_sync_data VALUES (?,?,?)",
                           ("legacy@example.com", json.dumps(payload), "2026-01-01T00:00:00+00:00"))
        connection.commit()
        connection.close()

    def tearDown(self):
        self.temp_directory.cleanup()

    def test_migrates_plaintext_atomically_and_is_idempotent(self):
        initialize_database(str(self.database))
        initialize_database(str(self.database))
        with connect(str(self.database)) as connection:
            user = connection.execute("SELECT * FROM user_accounts").fetchall()
            payload = json.loads(connection.execute("SELECT payload FROM user_sync_data").fetchone()["payload"])
            tables = {row["name"] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertEqual(1, len(user))
        self.assertTrue(verify_password(user[0]["password_hash"], "old-secret"))
        self.assertNotIn("password", payload)
        self.assertNotIn("email", payload)
        self.assertEqual({"user_accounts", "auth_sessions", "auth_throttle"} - tables, set())

    def test_failure_rolls_back_credential_and_payload_changes(self):
        def fail(*_):
            raise RuntimeError("injected")
        with self.assertRaises(RuntimeError):
            initialize_database(str(self.database), failure_hook=fail)
        connection = sqlite3.connect(self.database)
        payload = json.loads(connection.execute("SELECT payload FROM user_sync_data").fetchone()[0])
        count = connection.execute("SELECT COUNT(*) FROM user_accounts").fetchone()[0]
        connection.close()
        self.assertEqual("old-secret", payload["password"])
        self.assertEqual(0, count)


if __name__ == "__main__":
    unittest.main()
