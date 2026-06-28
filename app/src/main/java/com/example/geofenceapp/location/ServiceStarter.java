package com.example.geofenceapp.location;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Utility class for starting and stopping the GeofenceTrackingService.
 *
 * On Android 8.0+ (API 26+), services must be started as foreground services
 * using startForegroundService(). On older versions, startService() is used.
 * This class abstracts that version check into a single place.
 */
public final class ServiceStarter {

    private static final String TAG = "ServiceStarter";

    /** Private constructor — this class is used only through its static methods. */
    private ServiceStarter() {
    }

    /**
     * Starts the GeofenceTrackingService.
     * Uses startForegroundService() on Android O+ to comply with background execution limits.
     *
     * @param context application or activity context
     */
    public static void start(Context context) {
        Log.i(TAG, "Starting GeofenceTrackingService");
        Intent intent = new Intent(context, GeofenceTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stops the GeofenceTrackingService.
     *
     * @param context application or activity context
     */
    public static void stop(Context context) {
        Log.i(TAG, "Stopping GeofenceTrackingService");
        context.stopService(new Intent(context, GeofenceTrackingService.class));
    }
}
