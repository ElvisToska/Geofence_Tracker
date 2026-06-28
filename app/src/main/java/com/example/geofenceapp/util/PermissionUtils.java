package com.example.geofenceapp.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Utility class for checking and requesting runtime permissions.
 *
 * Handles the fine-location permission required for GPS tracking,
 * and on Android 13+ also requests the POST_NOTIFICATIONS permission
 * needed to show the foreground service notification.
 */
public final class PermissionUtils {

    /** Request code used when asking for location permission. */
    public static final int LOCATION_REQUEST = 100;

    /** Private constructor — this class is used only through its static methods. */
    private PermissionUtils() {
    }

    /**
     * Checks if the app has ACCESS_FINE_LOCATION permission.
     *
     * @param context application or activity context
     * @return true if fine location permission is already granted
     */
    public static boolean hasFineLocation(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests foreground location permission from the user.
     * On Android 13+ (API 33), also requests POST_NOTIFICATIONS.
     *
     * @param activity the activity to show the permission dialog from
     */
    public static void requestForegroundLocation(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ requires explicit notification permission for foreground services
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS},
                    LOCATION_REQUEST);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST);
        }
    }
}
