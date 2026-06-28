package com.example.geofenceapp.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;

import com.example.geofenceapp.util.AppPrefs;

/**
 * BroadcastReceiver that listens for GPS provider state changes.
 *
 * Registered in AndroidManifest.xml with the PROVIDERS_CHANGED action.
 * When GPS is turned on: restarts the tracking service if it was previously enabled.
 * When GPS is turned off: stops the tracking service to avoid running without GPS.
 *
 * This ensures the tracking service automatically recovers when the user
 * re-enables GPS without having to manually restart it.
 */
public class GpsStatusReceiver extends BroadcastReceiver {

    private static final String TAG = "GpsStatusReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check whether the GPS provider is currently enabled
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = manager != null && manager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        Log.i(TAG, "GPS status changed: " + (gpsEnabled ? "enabled" : "disabled")
                + ", service was enabled: " + AppPrefs.isServiceEnabled(context));

        if (gpsEnabled && AppPrefs.isServiceEnabled(context)) {
            // GPS came back on and the user had tracking enabled — restart the service
            ServiceStarter.start(context);
        } else {
            // GPS turned off — stop the service to avoid running without location
            ServiceStarter.stop(context);
        }
    }
}
