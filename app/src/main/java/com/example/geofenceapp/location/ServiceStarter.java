package com.example.geofenceapp.location;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ServiceStarter {
    private ServiceStarter() {
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, GeofenceTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, GeofenceTrackingService.class));
    }
}
