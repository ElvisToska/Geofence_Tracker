package com.example.geofenceapp.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String PREFS = "geofence_prefs";
    private static final String SESSION_ID = "session_id";
    private static final String SERVICE_ENABLED = "service_enabled";
    private static final String AUTH_USERNAME = "auth_username";
    private static final String AUTH_TOKEN = "auth_token";

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

    public static void setAuthState(Context context, String username, String token) {
        prefs(context).edit()
                .putString(AUTH_USERNAME, username)
                .putString(AUTH_TOKEN, token)
                .apply();
    }

    public static String getAuthUsername(Context context) {
        return prefs(context).getString(AUTH_USERNAME, "guest");
    }

    public static String getAuthToken(Context context) {
        return prefs(context).getString(AUTH_TOKEN, "");
    }

    public static boolean isLoggedIn(Context context) {
        return !"guest".equals(getAuthUsername(context)) && !getAuthToken(context).isEmpty();
    }

    public static void clearAuth(Context context) {
        prefs(context).edit()
                .remove(AUTH_USERNAME)
                .remove(AUTH_TOKEN)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
