# Tasks: 基于收藏的个性化电影推荐

## Implementation status

- [X] T001–T012 Foundation: backend schema/catalog/algorithm and Android domain, API, repository, DI.
- [X] T013–T020 US1: confirmed-only profile, stable/excluded results, account isolation, ViewModel/UI integration and stable movie-id navigation.
- [X] T021–T024 US2: contribution-based reasons, source labels and accessible reason rendering.
- [X] T025–T028 US3: deterministic repeated refresh, revision-aware repository and sync-safe state model.
- [X] T029–T032 US4: guest/insufficient/service-failure/empty fallbacks with retry and no core-flow coupling.
- [X] T033–T038 Metrics and polish: idempotent events, privacy-safe payloads, deterministic performance tests and removal of client random recommendation flows.
- [ ] T039 Full manual acceptance including real device and 20-person source-identification study — external blocker (no device/users in current environment).
- [X] T040 FR/SC/constitution traceability review recorded in `requirement-traceability.md` and `acceptance-evidence.md`.
- [X] T041 Canonical Maoyan candidate catalog: preserve Maoyan movie ID/title/poster and use the same ID for recommendation-to-detail navigation; automated catalog consistency evidence recorded.
- [X] T042 Stage 1 content ranking: Maoyan category/cast similarity, bounded popularity, freshness and deterministic tie-breaking.
- [X] T043 Stage 2 collaborative ranking: account-scoped item-item similarity from favorites, paid orders and recommendation interactions, with content-only fallback when cross-account data is insufficient.

## Verification commands

`python -m unittest discover -s backend -p "test_*.py"`  
`./gradlew.bat testDebugUnitTest --no-daemon`  
`./gradlew.bat assembleDebug --no-daemon`  
`./gradlew.bat compileDebugAndroidTestKotlin --no-daemon`  
`./gradlew.bat connectedDebugAndroidTest --no-daemon` (requires connected device)
