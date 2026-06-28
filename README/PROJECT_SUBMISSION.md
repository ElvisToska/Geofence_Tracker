# Geofence_Tracker

## Submission Document

**Course / Project:** Android Geofencing Application  
**Technology:** Android, Java, Google Maps, SQLite  
**Author:** Elvis  
**Date:** 2026-06-28

---

## Abstract

Geofence_Tracker is a native Android application that allows a user to define circular geofence areas on a map, start location tracking, detect entry and exit transitions, and review the latest session results. The application uses a foreground location service, a SQLite-backed content provider, and Google Maps to present and persist geofence activity. It also includes unit tests, provider tests, and emulator-based UI tests to verify core behavior.

---

## Chapter 1. Introduction

### 1.1 Purpose

The purpose of this project is to demonstrate a complete Android geofencing workflow in Java. The app covers the major pieces needed for a practical location-aware mobile application: permissions, map interaction, persistent storage, foreground tracking, transition detection, and results viewing.

### 1.2 Project Scope

The project includes:

- a main screen for navigation and tracking control
- a map screen for defining geofence areas
- a results screen for reviewing stored transitions
- a SQLite database with sessions, areas, and transitions
- a foreground service for geofence monitoring
- automated tests for logic, provider behavior, and UI flow

### 1.3 Main Objective

The main objective is to show how a user can:

1. place one or more circular areas on a map
2. start tracking device movement
3. detect when the device enters or exits those areas
4. store the results
5. review the latest session later

---

## Chapter 2. System Overview

Geofence_Tracker is organized into four major parts:

- presentation layer
- tracking layer
- data layer
- utility layer

```mermaid
flowchart TB
    User["User"]
    Main["MainActivity"]
    Map["MapActivity"]
    Results["ResultsMapActivity"]
    Service["GeofenceTrackingService"]
    Provider["GeofenceProvider"]
    Db["GeofenceDatabase"]
    Prefs["AppPrefs"]
    Utils["PermissionUtils / GeoMath / ServiceStarter"]

    User --> Main
    Main --> Login["LoginActivity"]
    Main --> Signup["SignupActivity"]
    Main --> Admin["AdminActivity"]
    Main --> Map
    Main --> Results
    Map --> Service
    Service --> Provider
    Provider --> Db
    Main --> Prefs
    Map --> Prefs
    Results --> Prefs
    Main --> Utils
    Map --> Utils
    Results --> Utils
    Service --> Utils
```

---

## Chapter 3. Application Flow

### 3.1 User Flow

1. The user opens the app.
2. The user defines geofence areas on the map.
3. The user starts tracking.
4. The app creates a new session and stores the selected areas.
5. The foreground service monitors location updates.
6. Entry and exit events are saved as transitions.
7. The results screen shows the latest session data.

### 3.2 Functional Flow

```mermaid
flowchart TD
    A["Open app"] --> B["Define areas"]
    B --> C["Place circles on map"]
    C --> D["Start tracking"]
    D --> E["Save session + areas"]
    E --> F["Run foreground service"]
    F --> G["Receive location updates"]
    G --> H{"Inside geofence?"}
    H -->|Yes| I["Store ENTER transition"]
    H -->|No| J["Store EXIT transition"]
    I --> K["Show notification"]
    J --> K["Show notification"]
    K --> L["View results"]
```

---

## Chapter 4. Architecture

### 4.1 Presentation Layer

#### `MainActivity`

Acts as the landing screen.

Responsibilities:

- request location permission when needed
- navigate to the map screen
- navigate to the results screen
- stop tracking
- display a status message

#### `MapActivity`

Lets the user define geofence areas.

Responsibilities:

- show the Google Map
- add circles with a long press
- remove circles by long pressing inside them
- create a session
- save selected areas
- start the tracking service

#### `ResultsMapActivity`

Shows the latest session results.

Responsibilities:

- draw saved geofence circles
- show enter/exit markers
- show the current device location when available
- pause or resume tracking
- display an empty state when no results exist

### 4.2 Tracking Layer

#### `GeofenceTrackingService`

Foreground service that monitors movement.

Responsibilities:

- request location updates
- filter out overly frequent updates
- detect inside/outside changes
- store transitions in the database
- show notifications

#### `GpsStatusReceiver`

Listens for GPS/provider changes.

Responsibilities:

- stop tracking when GPS is disabled
- restart tracking when GPS is enabled again and the app is active

#### `ServiceStarter`

Utility for starting and stopping the foreground service.

### 4.3 Data Layer

#### `GeofenceProvider`

Exposes app data through content URIs.

Responsibilities:

- insert sessions, areas, and transitions
- query current active areas
- query the latest session’s areas
- query the latest session’s transitions

#### `GeofenceDatabase`

Creates and manages the SQLite schema.

#### `GeofenceContract`

Defines table names, column names, URIs, and authority constants.

### 4.4 Utility Layer

#### `AppPrefs`

Stores small persistent flags:

- active session id
- service enabled state

#### `PermissionUtils`

Manages runtime permission checks and requests.

#### `GeoMath`

Implements distance calculation using the Haversine formula.

---

## Chapter 5. Data Model

### 5.1 Sessions

Represents a tracking run, scoped to one user.

Fields:

- `_ID`
- `username` (owner — foreign key to users)
- `started_at`
- `ended_at`
- `active`

### 5.2 Areas

Represents a geofence circle linked to a session. Scoped to the owning user.

Fields:

- `_ID`
- `username` (owner — foreign key to users)
- `session_id`
- `latitude`
- `longitude`
- `radius_meters`

### 5.3 Transitions

Represents an enter or exit event. Scoped to the owning user.

Fields:

- `_ID`
- `username` (owner — foreign key to users)
- `session_id`
- `area_id`
- `latitude`
- `longitude`
- `type` (ENTER or EXIT)
- `created_at`

```mermaid
erDiagram
    USERS ||--o{ SESSIONS : owns
    USERS ||--o{ PINS : assigned
    SESSIONS ||--o{ AREAS : contains
    SESSIONS ||--o{ TRANSITIONS : records
    AREAS ||--o{ TRANSITIONS : relates_to

    USERS {
        int _id
        string username
        string password_hash
        string password_salt
        string auth_token
        string role
        long created_at
    }

    SESSIONS {
        int _id
        string username
        long started_at
        long ended_at
        int active
    }

    AREAS {
        int _id
        string username
        int session_id
        double latitude
        double longitude
        double radius_meters
    }

    TRANSITIONS {
        int _id
        string username
        int session_id
        int area_id
        double latitude
        double longitude
        string type
        long created_at
    }

    PINS {
        int _id
        string username
        string label
        double latitude
        double longitude
        double radius_meters
        int active
        long created_at
    }
```

All data tables include a `username` column (foreign key to users) for per-user data isolation. The `GeofenceProvider` automatically filters every query and insert by the current user's username.

### 5.4 Users

Stores user accounts with hashed passwords and role-based access.

Fields: `_ID`, `username` (unique), `password_hash` (PBKDF2-WithHmacSHA256, 12,000 iterations, 256-bit key), `password_salt` (per-user 16-byte random salt), `auth_token` (issued on login, cleared on logout), `role` (`admin`/`user`/`guest`), `created_at`.

Passwords are never stored in plaintext.

### 5.5 Pins

Admin-assigned geofence points scoped to a specific user.

Fields: `_ID`, `username` (owner), `label`, `latitude`, `longitude`, `radius_meters`, `active`, `created_at`.

### 5.6 Accounts and Administration

The application provides a username/password account system layered over the content provider.

- **Signup** — `SignupActivity` uses its own layout (`activity_signup.xml`) with a password confirmation field. Validates that passwords match before registration. Includes a "Already have an account? Log in" navigation link.
- **Login** — `LoginActivity` authenticates via `AuthManager.login()`, which verifies the password hash and issues a random auth token (UUID + SecureRandom). Includes a "Don't have an account? Sign up" navigation link.
- **Session persistence** — `AuthSession` holds the current user in memory; `AuthManager.restoreSession` re-loads it from `AppPrefs` (SharedPreferences) on app start and resume, so data stays scoped to the correct user across process restarts.
- **Button visibility** — `MainActivity` dynamically toggles buttons: guests see Log In + Sign Up; regular users see Log Out only; admins see Log Out + Admin Panel. Updates on login, logout, and `onResume`.
- **Data isolation** — `GeofenceProvider` filters every record by the logged-in username, isolating each user's sessions, areas, transitions, and pins.
- **Admin account** — A seed administrator (`admin1404` / `admin1404`, role `admin`) is created by `GeofenceDatabase.seedAdmin` and verified by `AuthManager.ensureSeedAdmin` on every launch.
- **Admin panel** — `AdminActivity` provides full administration:
  - Add new user accounts
  - Delete users (admin self-deletion is blocked with a safety guard)
  - Reset any user's password (generates new salt + hash, clears auth token to force re-login)
  - Add/remove geofence pins for a target user
  - View all users with roles and pin counts (e.g., "admin1404 (admin) — 0 pins")
  - Non-admins are rejected with a toast and `finish()`

### 5.7 Database Migration

The database uses incremental `ALTER TABLE` migrations to preserve existing data:

- **v1 → v2**: Creates `users` and `pins` tables; adds `username` column to `sessions`, `areas`, and `transitions` (existing rows default to `'guest'`)
- **v2 → v3**: Re-seeds admin account to ensure it exists after migration

Uses `PRAGMA table_info()` to check column existence before altering. No data is lost during upgrades.

### 5.8 Logging

All Java files include `android.util.Log` calls with proper tags and levels:

- `Log.i` — important events: login, logout, signup, session creation, ENTER/EXIT transitions, service start/stop, GPS changes, DB migrations, user/pin management
- `Log.d` — debug info: activity lifecycle, camera moves, area toggles, location processing
- `Log.w` — warnings: failed auth, blocked admin deletion, missing permissions
- `Log.e` — errors: password hashing failures

Filter with: `adb logcat -s AuthManager GeofenceProvider GeofenceTrackingService MainActivity AdminActivity`

---

## Chapter 6. Diagrams

### 6.1 Use Case Diagram

```mermaid
flowchart LR
    U["User"]
    S["Geofence_Tracker App"]
    D["Android Device / GPS"]
    DB["SQLite / ContentProvider"]

    U -->|Define areas| S
    U -->|Start tracking| S
    U -->|View results| S
    U -->|Stop tracking| S
    D -->|Location updates| S
    S -->|Store sessions, areas, transitions| DB
    DB -->|Latest session results| S
```

### 6.2 Component Diagram

```mermaid
flowchart TB
    Main["MainActivity"]
    Map["MapActivity"]
    Results["ResultsMapActivity"]
    Service["GeofenceTrackingService"]
    Receiver["GpsStatusReceiver"]
    Provider["GeofenceProvider"]
    Db["GeofenceDatabase"]
    Prefs["AppPrefs"]
    Utils["PermissionUtils / GeoMath / ServiceStarter"]

    Main --> Map
    Main --> Results
    Main --> Prefs
    Main --> Utils
    Map --> Provider
    Map --> Prefs
    Map --> Utils
    Results --> Provider
    Results --> Prefs
    Results --> Utils
    Service --> Provider
    Service --> Prefs
    Service --> Utils
    Receiver --> Prefs
    Receiver --> Utils
    Provider --> Db
```

### 6.3 Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant Main as MainActivity
    participant Map as MapActivity
    participant Prov as GeofenceProvider
    participant Svc as GeofenceTrackingService
    participant Res as ResultsMapActivity

    User->>Main: Open app
    User->>Map: Define areas
    User->>Map: Start tracking
    Map->>Prov: Save session and areas
    Map->>Svc: Start service
    Svc->>Prov: Query active areas
    Svc->>Prov: Insert transitions
    User->>Res: View results
    Res->>Prov: Query latest session data
```

### 6.4 State Diagram

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> DefiningAreas: Open map
    DefiningAreas --> ReadyToTrack: Select at least one area
    ReadyToTrack --> Tracking: Start session
    Tracking --> Paused: Pause service / GPS off
    Paused --> Tracking: Resume service / GPS on
    Tracking --> ResultsVisible: Open results
    ResultsVisible --> Idle: Back to main
```

### 6.5 Database Flow

```mermaid
flowchart TD
    A["Insert session"] --> B["Mark previous session inactive"]
    B --> C["Insert area records"]
    C --> D["Tracking service starts"]
    D --> E["Query active areas"]
    E --> F["Detect geofence crossing"]
    F --> G["Insert transition record"]
    G --> H["Results screen queries latest session"]
```

---

## Chapter 7. Testing

The project includes **20 tests** across four categories: 16 instrumentation tests (require emulator or device) and 4 unit tests (run on JVM).

### 7.1 Unit Tests — 4 tests

**`GeoMathTest`** — validates the Haversine distance calculation:

1. Same-point distance is zero
2. Athens-to-Piraeus distance is approximately 8.5 km
3. Distance is symmetric (A→B equals B→A)
4. 100-meter threshold boundary behavior

### 7.2 Account Tests — 8 tests

**`AuthManagerTest`** — validates the authentication system:

1. Seed admin account is created and can log in with admin role
2. Signup, login, and logout work correctly for a regular user
3. `restoreSession` re-hydrates a logged-in user after a simulated process restart
4. Wrong password is rejected on login
5. Short password (under 6 characters) is rejected on signup
6. A regular user is not granted admin access
7. Duplicate usernames are rejected on signup
8. ContentProvider correctly stores and retrieves user records by username

### 7.3 Provider Tests — 5 tests

**`GeofenceProviderTest`** — validates database operations:

1. Session and area insert; current areas query returns correct data
2. 5-transition movement sequence; last queries return correct areas and transitions
3. Repeated same-side movements are stored without errors
4. Latest session is what results queries return (multi-session test)
5. Transition insert with enter and exit points

**`PinProviderTest`** — validates pin management:

1. Pin insert and delete scoped to a specific user

### 7.4 UI Flow Tests — 3 tests

**`ResultsMapActivityTest`**:

1. Results screen launches with seeded multi-session data, shows the correct (latest) session

**`AppFlowResultsUiTest`**:

1. Main screen "View Results" button navigates to results screen with logged-in user
2. Latest session data is visible in results query after navigation

---

## Chapter 8. Requirements and Notes

### 8.1 Runtime Requirements

- Android 6.0 or newer
- Google Play Services
- Google Maps API key
- location permission
- notification permission on Android 13+

### 8.2 Security Notes

The Google Maps API key is not stored in source control. It should be supplied locally in `local.properties`:

```properties
MAPS_API_KEY=YOUR_REAL_KEY
```

### 8.3 Compatibility Notes

The app is designed to be lightweight and broadly compatible, but actual behavior depends on:

- GPS availability
- Play Services
- permission grants
- screen size and font scaling
- device battery policies

---

## Chapter 9. Conclusion

Geofence_Tracker demonstrates a complete Android geofencing workflow in Java. It combines map-based interaction, a foreground tracking service, persistent storage, results visualization, and automated testing into a compact project structure. The codebase is intentionally organized to be understandable, maintainable, and suitable for demonstration or academic submission.

---

## References

- Android Developers documentation
- Google Maps Platform documentation
- AndroidX Test documentation
- SQLite and ContentProvider concepts in Android
- Haversine formula for distance calculation
- Project source code in this repository

