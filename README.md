# Geofence_Tracker Android App

Native Android project in Java for the geofence assignment.

Full project documentation, diagrams, and screen-by-screen explanation:

- [PROJECT_DOCUMENTATION.md](C:\Users\elvis\AndroidStudioProjects\android-geofence-java\PROJECT_DOCUMENTATION.md)
- [PROJECT_REPORT.md](C:\Users\elvis\AndroidStudioProjects\android-geofence-java\PROJECT_REPORT.md)
- [PROJECT_SUBMISSION.md](C:\Users\elvis\AndroidStudioProjects\android-geofence-java\PROJECT_SUBMISSION.md)

## What it includes

- `MainActivity`: menu for defining areas, stopping tracking, and viewing results.
- `MapActivity`: Google Map showing current location; long press adds a 100m circular area, long press inside an existing area removes it; `Start` creates a new session and starts tracking.
- `GeofenceProvider`: SQLite-backed `ContentProvider` for sessions, selected areas, and enter/exit transition points.
- `GeofenceTrackingService`: foreground location service that processes location updates only when the device moved more than 50m and at least 5 seconds passed, then compares against the selected areas using the Haversine formula.
- `GpsStatusReceiver`: stops the service when GPS is unavailable and restarts it when GPS returns if the user had tracking enabled.
- `ResultsMapActivity`: shows areas and enter/exit markers for the latest session plus the current device location, with a pause/resume service button.

## Setup

1. Open `android-geofence-java` in Android Studio.
2. Put your Google Maps API key in `local.properties`:

   ```properties
   MAPS_API_KEY=YOUR_REAL_KEY
   ```

3. Sync Gradle and run on a device or emulator with Google Play Services.

This workspace has Android SDK 34 and a cached Gradle 8.7 distribution installed, so the project currently uses `compileSdk 34`, `targetSdk 34`, and Android Gradle Plugin 8.6.1.
