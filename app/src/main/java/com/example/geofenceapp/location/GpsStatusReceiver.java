package com.example.geofenceapp.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import com.example.geofenceapp.util.AppPrefs;

public class GpsStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = manager != null && manager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (gpsEnabled && AppPrefs.isServiceEnabled(context)) {
            ServiceStarter.start(context);
        } else {
            ServiceStarter.stop(context);
        }
    }
}
