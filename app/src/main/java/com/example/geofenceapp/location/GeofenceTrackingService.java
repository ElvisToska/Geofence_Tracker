package com.example.geofenceapp.location;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.geofenceapp.MainActivity;
import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.util.AppPrefs;
import com.example.geofenceapp.util.GeoMath;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.HashSet;
import java.util.Set;

/**
 * Foreground service that continuously monitors the device's GPS position
 * and detects enter/exit transitions relative to defined geofence areas.
 *
 * The service:
 * 1. Requests high-accuracy location updates every 5 seconds / 50 meters
 * 2. For each update, compares the position against all areas in the active session
 * 3. Records ENTER/EXIT transitions in the database when the device crosses a geofence boundary
 * 4. Shows a persistent notification (required for foreground services on Android 8+)
 *
 * Uses FusedLocationProviderClient from Google Play Services for battery-efficient GPS.
 */
public class GeofenceTrackingService extends Service {

    private static final String TAG = "GeofenceTrackingService";

    /** Notification channel ID for the foreground service notification. */
    private static final String CHANNEL_ID = "geofence_tracking";

    /** Notification ID for the persistent tracking notification. */
    private static final int NOTIFICATION_ID = 42;

    /** Minimum time interval between location updates (5 seconds). */
    private static final long MIN_TIME_MS = 5000L;

    /** Minimum distance between location updates (50 meters). */
    private static final float MIN_DISTANCE_METERS = 50f;

    /** Google Play Services location client. */
    private FusedLocationProviderClient fusedLocationClient;

    /** Last location that was actually processed (used for deduplication). */
    private Location lastProcessedLocation;

    /** Timestamp of the last processed location. */
    private long lastProcessedAt;

    /** Flag to prevent requesting updates multiple times. */
    private boolean updatesStarted;

    /** Set of area IDs the device is currently inside (used to detect transitions). */
    private final Set<Long> insideAreaIds = new HashSet<>();

    /**
     * Callback that receives location updates from the FusedLocationProviderClient.
     * Delegates to handleLocation() for processing.
     */
    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null || locationResult.getLastLocation() == null) {
                return;
            }
            handleLocation(locationResult.getLastLocation());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created — starting foreground tracking");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Tracking geofence areas"));
        requestUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        AppPrefs.setServiceEnabled(this, true);
        requestUpdates();
        // START_STICKY: the system will restart the service if it gets killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed — stopping location updates");
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(callback);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This is a started service, not a bound service
        return null;
    }

    /**
     * Requests location updates from the FusedLocationProviderClient.
     * Stops the service if location permission is not granted.
     */
    private void requestUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — stopping service");
            stopSelf();
            return;
        }

        // Build a high-accuracy location request
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MIN_TIME_MS)
                .setMinUpdateIntervalMillis(MIN_TIME_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
                .build();

        // Only register the callback once
        if (updatesStarted) {
            return;
        }
        updatesStarted = true;
        fusedLocationClient.requestLocationUpdates(request, callback, getMainLooper());
        Log.d(TAG, "Location updates registered");
    }

    /**
     * Processes a new location update.
     * Applies time and distance filters to avoid processing redundant updates,
     * then compares the location against all active geofence areas.
     *
     * @param location the new GPS location
     */
    private void handleLocation(Location location) {
        long now = System.currentTimeMillis();

        // Skip if too soon or too close to the last processed location
        if (lastProcessedLocation != null) {
            boolean tooSoon = now - lastProcessedAt < MIN_TIME_MS;
            boolean tooClose = lastProcessedLocation.distanceTo(location) <= MIN_DISTANCE_METERS;
            if (tooSoon || tooClose) {
                return;
            }
        }

        lastProcessedLocation = location;
        lastProcessedAt = now;
        Log.d(TAG, String.format("Processing location: %.6f, %.6f", location.getLatitude(), location.getLongitude()));
        compareWithAreas(location);
    }

    /**
     * Compares the device's current location against all areas in the active session.
     * For each area, calculates the distance and determines if the device has
     * entered or exited the geofence boundary, recording transitions as needed.
     *
     * @param location the current GPS location
     */
    private void compareWithAreas(Location location) {
        // Query all areas belonging to the currently active session
        Cursor cursor = getContentResolver().query(GeofenceContract.Areas.CURRENT_URI, null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {
                long areaId = cursor.getLong(cursor.getColumnIndexOrThrow(GeofenceContract.Areas._ID));
                long sessionId = cursor.getLong(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.SESSION_ID));
                double areaLat = cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LATITUDE));
                double areaLng = cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LONGITUDE));
                double radius = cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.RADIUS_METERS));

                // Calculate distance from the device to the area center
                double distance = GeoMath.distanceMeters(location.getLatitude(), location.getLongitude(), areaLat, areaLng);
                boolean isInside = distance <= radius;
                boolean wasInside = insideAreaIds.contains(areaId);

                // Detect ENTER transition: device just moved inside the geofence
                if (isInside && !wasInside) {
                    insideAreaIds.add(areaId);
                    logTransition(sessionId, areaId, location, GeofenceContract.Transitions.ENTER);
                    notifyUser("Entered selected area");
                    Log.i(TAG, "ENTER transition: area=" + areaId + " distance=" + String.format("%.1fm", distance));
                }
                // Detect EXIT transition: device just moved outside the geofence
                else if (!isInside && wasInside) {
                    insideAreaIds.remove(areaId);
                    logTransition(sessionId, areaId, location, GeofenceContract.Transitions.EXIT);
                    notifyUser("Exited selected area");
                    Log.i(TAG, "EXIT transition: area=" + areaId + " distance=" + String.format("%.1fm", distance));
                }
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Records a geofence transition (ENTER or EXIT) in the database.
     *
     * @param sessionId the active session ID
     * @param areaId    the geofence area ID
     * @param location  the GPS coordinates where the transition occurred
     * @param type      either "ENTER" or "EXIT"
     */
    private void logTransition(long sessionId, long areaId, Location location, String type) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Transitions.SESSION_ID, sessionId);
        values.put(GeofenceContract.Transitions.AREA_ID, areaId);
        values.put(GeofenceContract.Transitions.LATITUDE, location.getLatitude());
        values.put(GeofenceContract.Transitions.LONGITUDE, location.getLongitude());
        values.put(GeofenceContract.Transitions.TYPE, type);
        getContentResolver().insert(GeofenceContract.Transitions.URI, values);
    }

    /**
     * Updates the notification with a new message (e.g., "Entered selected area").
     *
     * @param text the message to display
     */
    private void notifyUser(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 1, buildNotification(text));
        }
    }

    /**
     * Builds a notification for the foreground service.
     * Tapping the notification opens MainActivity.
     *
     * @param contentText the text to show in the notification body
     * @return the built Notification object
     */
    private Notification buildNotification(String contentText) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Geofence tracking")
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    /**
     * Creates the notification channel required on Android 8.0+ (API 26+).
     * Without this, notifications won't be displayed.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Geofence tracking",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
