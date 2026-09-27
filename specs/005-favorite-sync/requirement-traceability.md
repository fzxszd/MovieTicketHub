# Spec 005 Requirement → Implementation → Test/Evidence

| Requirement | Implementation | Test / evidence |
|---|---|---|
| FR-001–003 | `backend/favorites.py`; `FavoriteRepository`; movieId-scoped explicit set | `test_favorites.py::test_explicit_set_is_idempotent_and_tombstone_is_visible`; Android compile |
| FR-004 | `favorite_states` primary key and explicit target state | `test_explicit_set_is_idempotent_and_tombstone_is_visible` |
| FR-005 | `FavoriteSyncStatus`, Room desired/confirmed fields, status strings | `FavoriteMapperTest`; detail toolbar disables syncing |
| FR-006–007 | `GET /v1/favorites`, `FavoriteMoviesViewModel`, empty/error UI | `test_ordered_batch_duplicate_and_incremental_pull`; Compose source/build validation |
| FR-008 | Bearer-derived userId and server favorite tables | `test_requires_auth_and_rejects_identity_payload`; `test_accounts_are_isolated` |
| FR-009–010 | Global revision and ordered changes | `FavoriteSyncTest.test_ordered_batch_duplicate_and_incremental_pull` |
| FR-011–013 | Room operation queue, sync cursor, API batch, device hash | `test_batch_rejects_non_monotonic_sequence_and_overflow`; hash assertion |
| FR-014–015 | accountId Room partition, auth/session checks, account cleanup hooks | Android compilation; `SettingsViewModel` logout integration |
| FR-016 | Confirmed revision flow in `FavoriteRepository` | repository implementation review; automated backend revision tests |
| FR-017 | Legacy payload migration and Android legacy sync no longer writes favorites | `migrate_legacy_favorites`; existing backend suite remains green |

## Spec success criteria

- SC-001/002/003/004/005/006/007 are covered by the automated backend suite and Android unit/build checks listed in `acceptance-evidence.md`.
- SC-006 (offline UI), SC-007 (two physical devices and manual usability) remain externally blocked because no Android emulator/device or network-failure harness is available in this environment.
