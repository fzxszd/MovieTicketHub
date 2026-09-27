# Spec 005 Acceptance Evidence

Updated 2026-09-26.

## Automated checks

| Check | Result |
|---|---|
| Backend full unittest suite | PASS — 32 tests |
| New favorites API/sync tests | PASS — auth isolation, explicit set, tombstones, idempotency, ordered batches, validation, device hash |
| Android `testDebugUnitTest` | PASS |
| Android `compileDebugAndroidTestKotlin` | PASS |
| Android Room migration 8→9 and debug compilation | PASS via KSP/compile tasks |
| `assembleDebug` | PASS |

## Implemented behavior

- Bearer session derives userId; request payloads cannot provide email/userId.
- Server-authoritative `(userId,movieId)` state retains false tombstones.
- Client operation IDs, local sequence, desired/confirmed state, Room cursor and account-scoped cleanup are implemented.
- Device IDs are installation-scoped random UUIDs; server persists only SHA-256 hashes.
- Legacy favorites are imported only when mapped to an authenticated account and removed from the legacy payload authority.

## External blockers

- `adb devices` returned no devices and `connectedDebugAndroidTest` stopped with `DeviceException: No connected devices!`.
- `connectedDebugAndroidTest`, TalkBack, background/foreground lifecycle, network-offline recovery, and two physical-device convergence cannot run without an attached emulator/device.
- Manual three-account acceptance and 20-user usability evidence require human participants.
