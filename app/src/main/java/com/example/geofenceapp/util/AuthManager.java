package com.example.geofenceapp.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.geofenceapp.data.GeofenceContract;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * Central authentication manager that handles user registration, login,
 * logout, session restore, and role checking.
 *
 * All database access goes through the ContentResolver so it is scoped
 * by the GeofenceProvider. Passwords are stored only as salted PBKDF2 hashes.
 */
public final class AuthManager {

    private static final String TAG = "AuthManager";

    /** Default admin username, created on first launch. */
    public static final String ADMIN_USERNAME = "admin1404";

    /** Default admin password, created on first launch. */
    public static final String ADMIN_PASSWORD = "admin1404";

    /** Private constructor — this class is used only through its static methods. */
    private AuthManager() {
    }

    /**
     * Ensures the seed admin account exists in the database.
     * Called on every app launch from MainActivity.onCreate().
     * If the admin already exists, this method does nothing.
     *
     * @param context application or activity context for ContentResolver access
     */
    public static void ensureSeedAdmin(Context context) {
        // Check if the admin account already exists
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(ADMIN_USERNAME),
                null, null, null, null);
        if (cursor == null) {
            Log.w(TAG, "ensureSeedAdmin: cursor is null, provider may not be ready");
            return;
        }
        try {
            if (cursor.moveToFirst()) {
                Log.d(TAG, "Seed admin already exists");
                return;
            }
        } finally {
            cursor.close();
        }

        // Admin doesn't exist yet — create it with a hashed password
        String salt = PasswordHasher.generateSalt();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.USERNAME, ADMIN_USERNAME);
        values.put(GeofenceContract.Users.PASSWORD_HASH, PasswordHasher.hashPassword(ADMIN_PASSWORD, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_ADMIN);
        context.getContentResolver().insert(GeofenceContract.Users.URI, values);
        Log.i(TAG, "Seed admin account created: " + ADMIN_USERNAME);
    }

    /**
     * Registers a new user account.
     *
     * @param context  application or activity context
     * @param username desired username (will be trimmed and lowercased)
     * @param password desired password (must be at least 6 characters)
     * @return true if the account was created, false if validation failed or username is taken
     */
    public static boolean signUp(Context context, String username, String password) {
        String normalized = normalize(username);

        // Validate input: username must not be empty, password must be at least 6 chars
        if (normalized.isEmpty() || password == null || password.length() < 6) {
            Log.w(TAG, "signUp rejected: invalid username or password too short");
            return false;
        }

        // Check if the username is already taken
        if (usernameExists(context, normalized)) {
            Log.w(TAG, "signUp rejected: username '" + normalized + "' already exists");
            return false;
        }

        // Create the account with a salted password hash
        String salt = PasswordHasher.generateSalt();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.USERNAME, normalized);
        values.put(GeofenceContract.Users.PASSWORD_HASH, PasswordHasher.hashPassword(password, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_USER);
        Uri uri = context.getContentResolver().insert(GeofenceContract.Users.URI, values);

        boolean success = uri != null;
        Log.i(TAG, "signUp " + (success ? "succeeded" : "failed") + " for user: " + normalized);
        return success;
    }

    /**
     * Authenticates a user with their username and password.
     *
     * On success: generates a random auth token, stores it in the database,
     * saves the session to SharedPreferences (AppPrefs) and in-memory (AuthSession).
     *
     * @param context  application or activity context
     * @param username the username to log in with
     * @param password the plaintext password to verify
     * @return true if login succeeded, false if credentials are invalid
     */
    public static boolean login(Context context, String username, String password) {
        String normalized = normalize(username);

        // Look up the user in the database
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(normalized),
                null, null, null, null);
        if (cursor == null) {
            Log.w(TAG, "login failed: cursor is null for user '" + normalized + "'");
            return false;
        }

        try {
            if (!cursor.moveToFirst()) {
                Log.w(TAG, "login failed: user '" + normalized + "' not found");
                return false;
            }

            // Retrieve the stored salt and hash, then verify the password
            String salt = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.PASSWORD_SALT));
            String storedHash = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.PASSWORD_HASH));
            if (!PasswordHasher.verify(password, salt, storedHash)) {
                Log.w(TAG, "login failed: wrong password for user '" + normalized + "'");
                return false;
            }

            // Generate a random auth token (UUID + SecureRandom for extra entropy)
            String token = UUID.randomUUID().toString() + "-" + new SecureRandom().nextInt(Integer.MAX_VALUE);

            // Store the token in the database so we can validate it later
            context.getContentResolver().update(
                    GeofenceContract.Users.byUsernameUri(normalized),
                    tokenValues(token),
                    null, null);

            // Persist the session in SharedPreferences (survives process restarts)
            AppPrefs.setAuthState(context, normalized, token);

            // Set the in-memory session (used by GeofenceProvider for data scoping)
            AuthSession.set(normalized, token);

            Log.i(TAG, "login succeeded for user: " + normalized);
            return true;
        } finally {
            cursor.close();
        }
    }

    /**
     * Restores the in-memory AuthSession from SharedPreferences.
     *
     * Called on app start (MainActivity.onCreate) to handle the case where
     * the OS killed the process but the user was still logged in.
     * Without this, AuthSession.username() would return "guest" and the
     * ContentProvider would scope data to the wrong user.
     *
     * @param context application or activity context
     */
    public static void restoreSession(Context context) {
        // If we already have a session in memory, nothing to do
        if (AuthSession.isLoggedIn()) {
            return;
        }

        // If SharedPreferences says we're logged in, restore the session
        if (AppPrefs.isLoggedIn(context)) {
            String username = AppPrefs.getAuthUsername(context);
            AuthSession.set(username, AppPrefs.getAuthToken(context));
            Log.i(TAG, "Session restored from prefs for user: " + username);
        }
    }

    /**
     * Logs the current user out.
     *
     * Clears the auth token from the database, SharedPreferences, and in-memory session.
     *
     * @param context application or activity context
     */
    public static void logout(Context context) {
        String username = AppPrefs.getAuthUsername(context);

        // Clear the token in the database (only for real users, not "guest")
        if (!"guest".equals(username)) {
            context.getContentResolver().update(
                    GeofenceContract.Users.byUsernameUri(username),
                    tokenValues(""),
                    null, null);
        }

        // Clear SharedPreferences and in-memory session
        AppPrefs.clearAuth(context);
        AuthSession.clear();
        Log.i(TAG, "User logged out: " + username);
    }

    /**
     * Checks if the currently logged-in user has the admin role.
     *
     * @param context application or activity context
     * @return true if the current user's role is "admin"
     */
    public static boolean isAdmin(Context context) {
        return GeofenceContract.Users.ROLE_ADMIN.equals(getRole(context));
    }

    /**
     * Returns the username of the currently logged-in user.
     *
     * @param context application or activity context (unused but kept for API consistency)
     * @return the current username from AuthSession, or "guest" if not logged in
     */
    public static String currentUsername(Context context) {
        return AuthSession.username();
    }

    /**
     * Queries the database for the role of the currently logged-in user.
     *
     * @param context application or activity context
     * @return the user's role ("admin", "user"), or "guest" if not found
     */
    public static String getRole(Context context) {
        String username = currentUsername(context);
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(username),
                new String[]{GeofenceContract.Users.ROLE},
                null, null, null);
        if (cursor == null) {
            return GeofenceContract.Users.ROLE_GUEST;
        }
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        return GeofenceContract.Users.ROLE_GUEST;
    }

    /**
     * Checks whether a username already exists in the database.
     *
     * @param context  application or activity context
     * @param username the normalized username to check
     * @return true if the username is already taken
     */
    private static boolean usernameExists(Context context, String username) {
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(username),
                new String[]{GeofenceContract.Users.USERNAME},
                null, null, null);
        if (cursor == null) {
            return false;
        }
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    /**
     * Creates a ContentValues with just the auth_token field.
     * Used for updating or clearing a user's token in the database.
     */
    private static ContentValues tokenValues(String token) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.AUTH_TOKEN, token);
        return values;
    }

    /**
     * Normalizes a username by trimming whitespace and converting to lowercase.
     * Returns empty string if input is null.
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
