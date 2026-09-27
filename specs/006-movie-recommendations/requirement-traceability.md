# Requirement → Implementation → Test/Evidence

| Requirements | Implementation | Test/Evidence |
|---|---|---|
| FR-001, FR-005, FR-015, FR-018 | `backend/recommendations.py` reads only authenticated user's confirmed `favorite_states`; Android repository clears state | `test_recommendations.py`, `test_recommendation_isolation.py` |
| FR-002, FR-009 | `movie_catalog` availability filter; favorite/PAID exclusion; deterministic de-duplication | backend recommendation tests |
| FR-003, FR-011 | `sourceLabel` PERSONALIZED/fallback and Compose `RecommendationSection` | API response assertions; `assembleDebug` |
| FR-004 | SHARED_GENRE/SHARED_THEME contribution and honest fallback text | personalized reason assertions |
| FR-006–FR-008, FR-013 | favorite revision in runs, stable score/tie-break, sync-safe repository state | repeated request and revision unit coverage |
| FR-010–FR-014 | guest/insufficient/service failure/empty fallback with retryable response | backend fallback tests; UI error/retry branch |
| FR-016 | `recommendation_metrics` with idempotent event IDs and no raw preference fields | event idempotence test |
| FR-017 | no advertising or paid ranking code path | schema/algorithm review |

All 19 functional requirements are mapped; remaining SC-008 requires external participants (see acceptance evidence).

| FR-019, SC-009 | `sync_maoyan_catalog` refreshes `movie_catalog` from Maoyan's now-playing endpoint; `posterUrl` and Maoyan `movieId` are returned unchanged, while Android detail navigation retains that ID | mocked catalog-sync consistency test; live Maoyan sync smoke check; Android debug build |
| Hybrid ranking (Stage 1 + Stage 2) | `generate_recommendations` combines deterministic Maoyan content score with item-item collaborative score from favorites, paid orders and clicks; no-signal accounts keep content-only behavior | `test_collaborative_signal_surfaces_item_liked_by_similar_account`; repeated-ordering and isolation tests |
