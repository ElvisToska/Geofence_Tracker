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

public class GeofenceTrackingService extends Service {
    private static final String CHANNEL_ID = "geofence_tracking";
    private static final int NOTIFICATION_ID = 42;
    private static final long MIN_TIME_MS = 5000L;
    private static final float MIN_DISTANCE_METERS = 50f;

    private FusedLocationProviderClient fusedLocationClient;
    private Location lastProcessedLocation;
    private long lastProcessedAt;
    private boolean updatesStarted;
    private final Set<Long> insideAreaIds = new HashSet<>();

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Tracking geofence areas"));
        requestUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppPrefs.setServiceEnabled(this, true);
        requestUpdates();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(callback);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void requestUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MIN_TIME_MS)
                .setMinUpdateIntervalMillis(MIN_TIME_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
                .build();
        if (updatesStarted) {
            return;
        }
        updatesStarted = true;
        fusedLocationClient.requestLocationUpdates(request, callback, getMainLooper());
    }

    private void handleLocation(Location location) {
        long now = System.currentTimeMillis();
        if (lastProcessedLocation != null) {
            boolean tooSoon = now - lastProcessedAt < MIN_TIME_MS;
            boolean tooClose = lastProcessedLocation.distanceTo(location) <= MIN_DISTANCE_METERS;
            if (tooSoon || tooClose) {
                return;
            }
        }

        lastProcessedLocation = location;
        lastProcessedAt = now;
        compareWithAreas(location);
    }

    private void compareWithAreas(Location location) {
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
                double distance = GeoMath.distanceMeters(location.getLatitude(), location.getLongitude(), areaLat, areaLng);
                boolean isInside = distance <= radius;
                boolean wasInside = insideAreaIds.contains(areaId);

                if (isInside && !wasInside) {
                    insideAreaIds.add(areaId);
                    logTransition(sessionId, areaId, location, GeofenceContract.Transitions.ENTER);
                    notifyUser("Entered selected area");
                } else if (!isInside && wasInside) {
                    insideAreaIds.remove(areaId);
                    logTransition(sessionId, areaId, location, GeofenceContract.Transitions.EXIT);
                    notifyUser("Exited selected area");
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void logTransition(long sessionId, long areaId, Location location, String type) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Transitions.SESSION_ID, sessionId);
        values.put(GeofenceContract.Transitions.AREA_ID, areaId);
        values.put(GeofenceContract.Transitions.LATITUDE, location.getLatitude());
        values.put(GeofenceContract.Transitions.LONGITUDE, location.getLongitude());
        values.put(GeofenceContract.Transitions.TYPE, type);
        getContentResolver().insert(GeofenceContract.Transitions.URI, values);
    }

    private void notifyUser(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 1, buildNotification(text));
        }
    }

    private Notification buildNotification(String contentText) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
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
