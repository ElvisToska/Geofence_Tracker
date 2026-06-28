package com.example.geofenceapp.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Wrapper around SharedPreferences for persisting app state across process restarts.
 *
 * Stores two categories of data:
 * 1. Tracking state: the current session ID and whether the service is enabled.
 * 2. Auth state: the logged-in username and auth token (survives process kills
 *    so AuthManager.restoreSession() can re-hydrate AuthSession on next launch).
 */
public final class AppPrefs {

    /** SharedPreferences file name. */
    private static final String PREFS = "geofence_prefs";

    /** Key for the current tracking session ID. */
    private static final String SESSION_ID = "session_id";

    /** Key for whether the tracking service should be running. */
    private static final String SERVICE_ENABLED = "service_enabled";

    /** Key for the logged-in username. */
    private static final String AUTH_USERNAME = "auth_username";

    /** Key for the logged-in user's auth token. */
    private static final String AUTH_TOKEN = "auth_token";

    /** Private constructor — this class is used only through its static methods. */
    private AppPrefs() {
    }

    // =========================================================================
    // Tracking state
    // =========================================================================

    /** Saves the current tracking session ID. */
    public static void setSessionId(Context context, long sessionId) {
        prefs(context).edit().putLong(SESSION_ID, sessionId).apply();
    }

    /** Returns the current tracking session ID, or -1 if none is active. */
    public static long getSessionId(Context context) {
        return prefs(context).getLong(SESSION_ID, -1L);
    }

    /** Sets whether the geofence tracking service should be running. */
    public static void setServiceEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(SERVICE_ENABLED, enabled).apply();
    }

    /** Returns true if the geofence tracking service is enabled. */
    public static boolean isServiceEnabled(Context context) {
        return prefs(context).getBoolean(SERVICE_ENABLED, false);
    }

    // =========================================================================
    // Auth state
    // =========================================================================

    /**
     * Saves the authenticated user's username and token to SharedPreferences.
     * Called after a successful login so the session survives process restarts.
     *
     * @param context  application or activity context
     * @param username the authenticated username
     * @param token    the auth token issued at login
     */
    public static void setAuthState(Context context, String username, String token) {
        prefs(context).edit()
                .putString(AUTH_USERNAME, username)
                .putString(AUTH_TOKEN, token)
                .apply();
    }

    /** Returns the stored username, or "guest" if nobody is logged in. */
    public static String getAuthUsername(Context context) {
        return prefs(context).getString(AUTH_USERNAME, "guest");
    }

    /** Returns the stored auth token, or empty string if nobody is logged in. */
    public static String getAuthToken(Context context) {
        return prefs(context).getString(AUTH_TOKEN, "");
    }

    /**
     * Checks whether a user is currently logged in (persisted state).
     *
     * @return true if both a non-guest username and a non-empty token are stored
     */
    public static boolean isLoggedIn(Context context) {
        return !"guest".equals(getAuthUsername(context)) && !getAuthToken(context).isEmpty();
    }

    /** Clears the stored auth state (called on logout). */
    public static void clearAuth(Context context) {
        prefs(context).edit()
                .remove(AUTH_USERNAME)
                .remove(AUTH_TOKEN)
                .apply();
    }

    // =========================================================================
    // Internal
    // =========================================================================

    /** Returns the app-wide SharedPreferences instance. */
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
