# Acceptance Criteria and Evidence

- Personalized source, real reasons, favorite/paid/unavailable exclusion: automated backend tests pass.
- Guest and insufficient-favorite fallback: automated backend tests pass.
- Stable repeated ordering: deterministic score and tie-break implementation plus repeated request test.
- Account isolation/privacy: user-scoped SQL and isolation test; event payload contains only eventId/movieId/action.
- Android loading/content/error/retry rendering compiles; recommendation failure is isolated from movie browsing.
- Python suite and Android unit/build verification are recorded in the completion report.
- Connected-device instrumentation and the 20-person recognition study remain external blockers: `adb devices` has no connected device and no participant pool is available.
- Maoyan source consistency: the backend synchronizes the same now-playing endpoint used by Android. The normalized catalog retains Maoyan movie ID, title, poster URL, category, and cast signals; an automated mocked-sync test and live smoke check passed.
- Hybrid recommendation: content ranking uses Maoyan category/cast, bounded popularity and release freshness; collaborative ranking uses cross-account favorites, paid orders and recommendation clicks. A dedicated cross-account test verifies a collaboratively related movie is surfaced with a `COLLABORATIVE` reason.
