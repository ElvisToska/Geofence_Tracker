# Geofence_Tracker Android App

Native Android application written in Java for defining geofence areas, tracking location, detecting enter/exit transitions, managing user accounts, and reviewing session results on a map.

Full project documentation:

- [PROJECT_DOCUMENTATION.md](README/PROJECT_DOCUMENTATION.md) — technical reference with diagrams
- [PROJECT_REPORT.md](README/PROJECT_REPORT.md) — narrative project report
- [PROJECT_SUBMISSION.md](README/PROJECT_SUBMISSION.md) — formal academic submission document

## Features

### Geofence Tracking
- `MapActivity`: Google Map where users long-press to place 100m circular geofence areas (long-press inside an existing circle to remove it). Tap "Start" to create a session and begin tracking.
- `GeofenceTrackingService`: foreground location service that processes GPS updates (filtered by 50m distance and 5s interval), compares against active areas using the Haversine formula, and records ENTER/EXIT transitions.
- `ResultsMapActivity`: displays the most recent session's geofence circles, enter markers (green), exit markers (orange), and the current device location. Includes pause/resume service control.
- `GpsStatusReceiver`: automatically stops the service when GPS is disabled and restarts it when GPS returns (if the user had tracking enabled).

### User Accounts & Authentication
- `LoginActivity` / `SignupActivity`: username/password authentication with a dedicated signup layout (`activity_signup.xml`) featuring a password confirmation field and "Already have an account? Log in" / "Don't have an account? Sign up" navigation links.
- `PasswordHasher`: passwords are stored as salted PBKDF2-WithHmacSHA256 hashes (12,000 iterations, 256-bit key, per-user 16-byte random salt). Plaintext passwords are never stored.
- `AuthManager`: handles signup, login (issues random auth token), logout, session restore after process restart, and admin role checking.
- `AuthSession`: in-memory holder for the current user's credentials, re-hydrated from `AppPrefs` (SharedPreferences) on app start via `AuthManager.restoreSession()`.
- `GeofenceProvider`: automatically scopes all database queries and inserts to the current user's username, ensuring complete per-user data isolation.

### Administration
- `AdminActivity`: accessible only to users with the `admin` role. Provides:
  - Add/delete user accounts (admin self-deletion is blocked)
  - Reset any user's password (generates new salt and hash, forces re-login)
  - Add/remove geofence pins assigned to a specific user
  - View all users with roles and pin counts
  - View detailed pin list for a target user

### UI Behavior
- `MainActivity` toggles button visibility based on auth state:
  - **Guest**: shows Log In + Sign Up buttons, hides Log Out + Admin Panel
  - **Regular user**: shows Log Out, hides Log In + Sign Up + Admin Panel
  - **Admin**: shows Log Out + Admin Panel, hides Log In + Sign Up
- Status text updates to reflect auth state, permission state, and tracking state.

### Data Layer
- `GeofenceProvider`: SQLite-backed ContentProvider for sessions, areas, transitions, users, and pins.
- `GeofenceDatabase`: creates and migrates the database schema. Uses `ALTER TABLE` migrations (version 3) to preserve existing data — does NOT drop tables on upgrade.
- `GeofenceContract`: defines all table names, column names, content URIs, and role constants.
- All data tables (sessions, areas, transitions) include a `username` column for per-user data isolation.

### Logging
All Java files include `android.util.Log` calls with appropriate levels:
- `Log.i`: login, logout, signup, session creation, transitions, service start/stop, GPS changes, DB migrations
- `Log.d`: activity lifecycle, camera moves, area add/remove, location processing
- `Log.w`: failed login, rejected signup, blocked admin deletion, missing permissions
- `Log.e`: password hashing failures

Filter logs with: `adb logcat -s AuthManager GeofenceProvider GeofenceTrackingService MainActivity AdminActivity`

## Testing

**20 tests total** — 16 instrumentation (emulator) + 4 unit (JVM):

| Test class | Type | Tests | What it covers |
|---|---|---|---|
| `GeoMathTest` | Unit | 4 | Distance calculation: zero, symmetry, Athens–Piraeus, 100m threshold |
| `AuthManagerTest` | Instrumentation | 8 | Seed admin login, signup/login/logout, session restore, wrong password, short password, duplicate rejection, non-admin check |
| `GeofenceProviderTest` | Instrumentation | 4 | Session/area/transition insert, current/last queries, multi-session, repeated movements |
| `PinProviderTest` | Instrumentation | 1 | Pin insert/delete scoped to user |
| `ResultsMapActivityTest` | Instrumentation | 1 | Results screen launch with seeded multi-session data |
| `AppFlowResultsUiTest` | Instrumentation | 2 | Main→Results navigation with login, latest session visibility |

## Setup

1. Open `android-geofence-java` in Android Studio.
2. Put your Google Maps API key in `local.properties`:

   ```properties
   MAPS_API_KEY=YOUR_REAL_KEY
   ```

3. Sync Gradle and run on a device or emulator with Google Play Services.

## Build Configuration

- `compileSdk 34`, `targetSdk 34`, `minSdk 23` (Android 6.0+)
- Android Gradle Plugin 8.6.1, Gradle 8.7
- Google Play Services required for Maps and FusedLocationProvider

## Default Admin Account

On first launch, the app creates a seed admin account:
- **Username**: `admin1404`
- **Password**: `admin1404`

## Security Notes

- Passwords are stored only as salted PBKDF2 hashes (never plaintext)
- Auth tokens are UUID + SecureRandom, cleared on logout
- The Google Maps API key is not in source control — it lives in `local.properties`
- The ContentProvider is not exported (`android:exported="false"`)
- Per-user data isolation is enforced at the provider level
