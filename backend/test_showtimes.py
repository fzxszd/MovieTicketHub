import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from app import connect, create_app


NOW = datetime(2026, 9, 25, 2, 0, tzinfo=timezone.utc)  # 10:00 Asia/Shanghai


class ShowtimeApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_directory = tempfile.TemporaryDirectory()
        self.database = Path(self.temp_directory.name) / "showtimes.db"
        self.app = create_app(self.database, {"TESTING": True, "NOW_PROVIDER": lambda: NOW})
        self.client = self.app.test_client()

    def tearDown(self):
        self.temp_directory.cleanup()

    def test_available_dates_are_cinema_local_future_dates(self):
        response = self.client.get("/v1/movies/1/showtime-dates?cityCode=CN-SH")
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(1, body["movieId"])
        self.assertEqual(sorted(body["dates"]), body["dates"])
        self.assertIn("2026-09-25", body["dates"])
        self.assertNotIn("2026-09-24", body["dates"])

    def test_query_returns_integer_money_timezone_and_only_matching_sellable_cinemas(self):
        response = self.client.get(
            "/v1/movies/1/cinema-showtimes?cityCode=CN-SH&date=2026-09-25"
            "&district=浦东新区&startTime=18:00&maxPriceMinor=6000&sort=price"
        )
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual("price", body["appliedSort"])
        self.assertTrue(body["cinemas"])
        for cinema in body["cinemas"]:
            self.assertEqual("浦东新区", cinema["district"])
            self.assertIsInstance(cinema["minimumPrice"]["amountMinor"], int)
            self.assertEqual("Asia/Shanghai", cinema["timeZone"])
            self.assertTrue(cinema["showtimes"])
            self.assertTrue(all(showtime["selectable"] for showtime in cinema["showtimes"]))

    def test_distance_without_coordinates_degrades_without_hiding_results(self):
        response = self.client.get(
            "/v1/movies/1/cinema-showtimes?cityCode=CN-SH&date=2026-09-25&sort=distance"
        )
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual("distance", body["requestedSort"])
        self.assertEqual("recommended", body["appliedSort"])
        self.assertEqual("LOCATION_UNAVAILABLE", body["sortNotice"])
        self.assertTrue(body["cinemas"])

    def test_filters_can_be_cleared_and_combined_with_stable_sorting(self):
        priced = self.client.get(
            "/v1/movies/1/cinema-showtimes?cityCode=CN-SH&date=2026-09-25&sort=price"
        ).get_json()["cinemas"]
        prices = [cinema["minimumPrice"]["amountMinor"] for cinema in priced]
        self.assertEqual(sorted(prices), prices)
        no_match = self.client.get(
            "/v1/movies/1/cinema-showtimes?cityCode=CN-SH&date=2026-09-25&maxPriceMinor=1"
        ).get_json()
        self.assertEqual([], no_match["cinemas"])

    def test_validation_reports_unchanged_changed_and_unavailable(self):
        unchanged = self.client.get("/v1/showtimes/st_sh_001/validation?observedVersion=1")
        self.assertEqual(200, unchanged.status_code)
        self.assertEqual("UNCHANGED", unchanged.get_json()["result"])
        with connect(str(self.database)) as connection:
            connection.execute(
                "UPDATE showtimes SET base_price_minor=base_price_minor+500,version=version+1 WHERE id='st_sh_001'"
            )
        changed = self.client.get("/v1/showtimes/st_sh_001/validation?observedVersion=1").get_json()
        self.assertEqual("CHANGED", changed["result"])
        self.assertIn("PRICE", changed["changedFields"])
        with connect(str(self.database)) as connection:
            connection.execute("UPDATE showtimes SET sales_status='CANCELLED',version=version+1 WHERE id='st_sh_001'")
        unavailable = self.client.get("/v1/showtimes/st_sh_001/validation?observedVersion=2").get_json()
        self.assertEqual("UNAVAILABLE", unavailable["result"])
        self.assertEqual("CANCELLED", unavailable["latest"]["unavailableReason"])

    def test_invalid_inputs_and_unknown_showtime_use_contract_errors(self):
        self.assertEqual(400, self.client.get("/v1/movies/0/showtime-dates?cityCode=CN-SH").status_code)
        self.assertEqual(400, self.client.get(
            "/v1/movies/1/cinema-showtimes?cityCode=CN-SH&date=bad"
        ).status_code)
        self.assertEqual(404, self.client.get(
            "/v1/showtimes/missing/validation?observedVersion=1"
        ).status_code)


if __name__ == "__main__":
    unittest.main()
