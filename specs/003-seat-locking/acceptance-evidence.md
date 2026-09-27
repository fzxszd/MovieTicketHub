# 003 Seat Locking — Acceptance Evidence

Updated: 2026-09-25

## Automated evidence

| Check | Result | Evidence |
|---|---|---|
| Backend functional suite | PASS | `python -m unittest discover -s backend -p "test_*.py"` — 23 tests passed |
| 100-way lock contention and snapshot p95 | PASS | `backend/test_seat_concurrency.py`; exactly one 201, retries 200/409, snapshot p95 < 500 ms |
| Android unit tests and debug build | PASS | `gradlew.bat testDebugUnitTest assembleDebug` |
| Instrumented Android tests | BLOCKED | No ADB device/emulator attached (`adb devices` returned no devices) |

## Scenario evidence

| Scenario | Automated coverage | Status |
|---|---|---|
| A — authoritative layout | `test_layout_expiry_lifecycle_and_fingerprint_recovery`, `SeatMapperTest` | PASS |
| B — atomic locking | `test_authenticated_snapshot_and_atomic_idempotent_lock`, `test_conflict_is_all_or_nothing_and_logout_releases`, 100-way concurrency test | PASS |
| C — lifecycle | expiry/logout/SOLD protection tests | PASS except device background/resume UI exercise |

## Manual / external blockers

1. Run `gradlew.bat connectedDebugAndroidTest` with a started emulator or USB-debuggable device.
2. Perform the three Quickstart manual scenarios, including TalkBack seat semantics and app background/resume.
