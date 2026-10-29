# Football Scores

Android football scores app built with Kotlin and Jetpack Compose.

## Project layout

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/footballscores/
│           ├── MainActivity.kt
│           ├── data/       # API client and repository
│           ├── model/      # API DTOs and match model
│           └── ui/         # Compose screen and ViewModel
├── build.gradle.kts        # Shared Gradle plugin versions
└── settings.gradle.kts     # Android app module and repositories
```

## Setup

1. Open the project in Android Studio and sync Gradle.
2. Add your key to the root `local.properties` file (it is git-ignored):
   `FOOTBALL_API_KEY=your_key_here`
3. Run the `app` configuration on a device or emulator.

## How it stays inside 10 requests/minute
- One call to `/v4/matches?dateFrom=..&dateTo=..` returns every match for all competitions in your plan.
- Polling interval is adaptive: 30s when something is live or about to start, 2 min otherwise, 10 min for other days.
- Polling is tied to the app lifecycle (stops in background).
- On HTTP 429 the app waits for `X-RequestCounter-Reset` seconds before retrying.

## Known limits / next steps
- Free plan scores are typically **delayed**, so "realtime" is near-realtime at best. Check the plan page on football-data.org.
- The API key is embedded in the APK and can be extracted. For a public release, put a small proxy server in front (it can also cache, so thousands of users share the same 10 req/min).
- Next: Room cache for offline, pull-to-refresh, match detail screen (`/v4/matches/{id}`), standings (`/v4/competitions/{code}/standings`), push notifications via a backend + FCM.
