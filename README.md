# MovieTicketHub

MovieTicketHub is a full-stack Android movie discovery and cinema-ticketing app. It combines a Jetpack Compose client with a Flask + SQLite service for account security, showtime inventory, seat locking, orders, simulated payments, favorites synchronization, and recommendations.

> This is a development/demo implementation. Payment providers are simulated; do not use it to process real money.

## Highlights

- Browse now-playing and upcoming movies from the Maoyan movie API; search titles and view movie details, cast, and synopsis.
- Browse as a guest, with authentication gates for favorites, seat selection, and purchases.
- Register, sign in, restore a session, and sign out. Tokens are encrypted on-device and revocable on the server.
- Browse showtimes by city, cinema, date, district, time range, price range, distance, and sort order.
- Select one to six seats from a live seat map. The server owns availability and creates a 10-minute expiring seat lock.
- Request a server-authoritative quote, create an order idempotently, complete simulated Alipay/WeChat payments, view tickets, and refund paid orders.
- Keep favorites offline-first with Room, then reconcile changes across devices using an operation queue, idempotency keys, and server revisions.
- Receive explainable personalized recommendations based on confirmed favorites, purchases, popularity, freshness, and collaborative signals; fall back safely for guests or cold starts.

## Screenshots

The following emulator captures are included in the repository.

<p align="center">
  <img src="screenshots/splash_screen.png" alt="Splash screen" width="23%" />
  <img src="screenshots/login_screen.png" alt="Login screen" width="23%" />
  <img src="screenshots/movies_list_screen_1.png" alt="Now-playing movie list" width="23%" />
  <img src="screenshots/movies_list_screen_2.png" alt="Upcoming movie list" width="23%" />
</p>

<p align="center">
  <img src="screenshots/movie_detail_screen_1.png" alt="Movie detail" width="30%" />
  <img src="screenshots/movie_detail_screen_2.png" alt="Movie details and cast" width="30%" />
  <img src="screenshots/favorite_movies.png" alt="Favorite movies" width="30%" />
</p>

## Architecture

```text
Jetpack Compose UI
  └─ ViewModel + StateFlow (MVI-style unidirectional state)
       └─ Domain repositories
            ├─ Maoyan movie APIs through Ktor
            ├─ Flask REST API through Ktor + Bearer tokens
            └─ Room local cache and offline favorite-operation queue

Flask API
  ├─ auth          password hashing, sessions, throttling, logout
  ├─ showtimes     local-time filtering, distance sorting, versions
  ├─ seats         live inventory, locks, expiry, concurrency control
  ├─ orders        quotes, orders, tickets, refunds, audit events
  ├─ payments      simulated provider attempts and signed webhooks
  ├─ favorites     operation log, revisions, incremental synchronization
  └─ recommendations hybrid ranking, fallbacks, event metrics
       └─ SQLite (development storage)
```

The Android client is structured into `presentation`, `domain`, and `data` layers. Server-side business ownership is deliberate: the client renders state and makes requests, while the server is the final authority for sessions, inventory, prices, payment outcomes, and confirmed favorites.

## Key Technical Decisions

| Area | Approach |
| --- | --- |
| Session security | Werkzeug scrypt password hashes; only SHA-256 token digests are stored server-side; Android stores tokens with Android Keystore AES/GCM. |
| Seat concurrency | SQLite `BEGIN IMMEDIATE`, availability-guarded updates, idempotency keys, a seat-set fingerprint, and expiry cleanup prevent duplicate active locks. |
| Money and orders | All amounts use integer minor units. Quote acceptance, order creation, payment attempts, tickets, and state events are server authoritative and idempotent. |
| Favorites | Room separates desired and confirmed state. A per-account operation queue and revision cursor make retries and multi-device convergence safe. |
| Recommendations | Deterministic hybrid ranking excludes already-favorited/purchased films, records explainable result snapshots, and supports event de-duplication. |

For a deeper implementation walkthrough, see [the project technical summary](docs/项目技术总结.md) and [the Spring Boot migration assessment](docs/SpringBoot后端重构成本评估.md).

## Tech Stack

| Layer | Technology |
| --- | --- |
| Android | Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, StateFlow, Coroutines |
| Client data | Ktor 3, kotlinx.serialization, Coil 3, Room 2.6, Koin 4, Android Keystore |
| Backend | Python 3, Flask 3, SQLite, standard-library HMAC |
| Testing | Python `unittest`, Flask test client, JUnit, Ktor Mock, Room Testing, Compose UI tests |

## Run Locally

### Prerequisites

- Android Studio with an Android SDK/emulator (Android API 24+)
- JDK compatible with the Gradle build
- Python 3.12+ recommended

### 1. Start the backend

From the repository root:

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt
backend/.venv/Scripts/python.exe backend/app.py
```

The development API listens on `http://127.0.0.1:5000`. The Android debug build is already configured to reach the host machine from the emulator at `http://10.0.2.2:5000`.

### 2. Run the Android app

Open the root directory in Android Studio, select an emulator or device, then run the `app` configuration. Alternatively:

```powershell
.\gradlew.bat assembleDebug
```

The app can browse movies as a guest. Start the Flask service before using login, favorites, showtimes, seat selection, payment, orders, or recommendations.

## Verify

Run the backend suite:

```powershell
backend/.venv/Scripts/python.exe -m unittest discover -s backend -p "test_*.py" -v
```

Run Android unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

The backend suite covers authentication, session revocation, showtime filtering, lock concurrency, idempotency, payment callbacks, refunds, favorites synchronization, and recommendation isolation.

## Project Structure

```text
app/                    Android application
  src/main/java/        Compose UI, ViewModels, repositories, Room, Ktor DTOs
  src/test/             Local unit tests
  src/androidTest/      Device/UI and Room migration tests
backend/                Flask service and Python test suite
specs/                  Feature specifications, data models, API contracts, and acceptance evidence
screenshots/            App screenshots used in this README
docs/                   Technical and architecture documentation
```

## Notes for Deployment

- The bundled SQLite database is intended for local development. Do not commit `backend/data/*.db`.
- Deploy the API behind HTTPS; the release build must point to a real HTTPS API endpoint instead of the placeholder URL.
- Move to a shared database such as PostgreSQL before running multiple backend instances or integrating a real payment provider.
