package com.example.geofenceapp.util;

import android.util.Log;

/**
 * In-memory holder for the currently logged-in user's credentials.
 *
 * This is a static singleton that lives as long as the process.
 * When the OS kills and recreates the process, these values reset to "guest" / "",
 * so AuthManager.restoreSession() must be called on app start to re-hydrate
 * from the persisted SharedPreferences (AppPrefs).
 */
public final class AuthSession {

    private static final String TAG = "AuthSession";

    /** The username of the currently logged-in user, or "guest" if nobody is logged in. */
    private static String currentUsername = "guest";

    /** The auth token issued at login, or empty string if not logged in. */
    private static String currentToken = "";

    /** Private constructor — this class is used only through its static methods. */
    private AuthSession() {
    }

    /**
     * Sets the in-memory session to the given username and token.
     * Called after a successful login or when restoring from SharedPreferences.
     *
     * @param username the authenticated username, or null/empty to default to "guest"
     * @param token    the auth token, or null to default to ""
     */
    public static void set(String username, String token) {
        currentUsername = username == null || username.isEmpty() ? "guest" : username;
        currentToken = token == null ? "" : token;
        Log.i(TAG, "Session set for user: " + currentUsername);
    }

    /** Returns the current username ("guest" if not logged in). */
    public static String username() {
        return currentUsername;
    }

    /** Returns the current auth token (empty string if not logged in). */
    public static String token() {
        return currentToken;
    }

    /**
     * Checks whether a real user is logged in.
     *
     * @return true if the username is not "guest" and a non-empty token exists
     */
    public static boolean isLoggedIn() {
        return !"guest".equals(currentUsername) && !currentToken.isEmpty();
    }

    /** Clears the session back to the default "guest" state. */
    public static void clear() {
        Log.i(TAG, "Session cleared for user: " + currentUsername);
        currentUsername = "guest";
        currentToken = "";
    }
}
