package com.example.geofenceapp.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String PREFS = "geofence_prefs";
    private static final String SESSION_ID = "session_id";
    private static final String SERVICE_ENABLED = "service_enabled";

    private AppPrefs() {
    }

    public static void setSessionId(Context context, long sessionId) {
        prefs(context).edit().putLong(SESSION_ID, sessionId).apply();
    }

    public static long getSessionId(Context context) {
        return prefs(context).getLong(SESSION_ID, -1L);
    }

    public static void setServiceEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(SERVICE_ENABLED, enabled).apply();
    }

    public static boolean isServiceEnabled(Context context) {
        return prefs(context).getBoolean(SERVICE_ENABLED, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
