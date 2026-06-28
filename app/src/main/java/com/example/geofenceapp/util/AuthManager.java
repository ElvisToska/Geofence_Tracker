package com.example.geofenceapp.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.example.geofenceapp.data.GeofenceContract;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

public final class AuthManager {
    public static final String ADMIN_USERNAME = "admin1404";
    public static final String ADMIN_PASSWORD = "admin1404";

    private AuthManager() {
    }

    public static void ensureSeedAdmin(Context context) {
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(ADMIN_USERNAME),
                null,
                null,
                null,
                null);
        if (cursor == null) {
            return;
        }
        try {
            if (cursor.moveToFirst()) {
                return;
            }
        } finally {
            cursor.close();
        }

        String salt = PasswordHasher.generateSalt();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.USERNAME, ADMIN_USERNAME);
        values.put(GeofenceContract.Users.PASSWORD_HASH, PasswordHasher.hashPassword(ADMIN_PASSWORD, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_ADMIN);
        context.getContentResolver().insert(GeofenceContract.Users.URI, values);
    }

    public static boolean signUp(Context context, String username, String password) {
        String normalized = normalize(username);
        if (normalized.isEmpty() || password == null || password.length() < 6) {
            return false;
        }

        if (usernameExists(context, normalized)) {
            return false;
        }

        String salt = PasswordHasher.generateSalt();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.USERNAME, normalized);
        values.put(GeofenceContract.Users.PASSWORD_HASH, PasswordHasher.hashPassword(password, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_USER);
        Uri uri = context.getContentResolver().insert(GeofenceContract.Users.URI, values);
        return uri != null;
    }

    public static boolean login(Context context, String username, String password) {
        String normalized = normalize(username);
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(normalized),
                null,
                null,
                null,
                null);
        if (cursor == null) {
            return false;
        }
        try {
            if (!cursor.moveToFirst()) {
                return false;
            }
            String salt = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.PASSWORD_SALT));
            String storedHash = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.PASSWORD_HASH));
            if (!PasswordHasher.verify(password, salt, storedHash)) {
                return false;
            }
            String token = UUID.randomUUID().toString() + "-" + new SecureRandom().nextInt(Integer.MAX_VALUE);
            context.getContentResolver().update(
                    GeofenceContract.Users.byUsernameUri(normalized),
                    tokenValues(token),
                    null,
                    null);
            AppPrefs.setAuthState(context, normalized, token);
            AuthSession.set(normalized, token);
            return true;
        } finally {
            cursor.close();
        }
    }

    public static void restoreSession(Context context) {
        if (AuthSession.isLoggedIn()) {
            return;
        }
        if (AppPrefs.isLoggedIn(context)) {
            AuthSession.set(AppPrefs.getAuthUsername(context), AppPrefs.getAuthToken(context));
        }
    }

    public static void logout(Context context) {
        String username = AppPrefs.getAuthUsername(context);
        if (!"guest".equals(username)) {
            context.getContentResolver().update(
                    GeofenceContract.Users.byUsernameUri(username),
                    tokenValues(""),
                    null,
                    null);
        }
        AppPrefs.clearAuth(context);
        AuthSession.clear();
    }

    public static boolean isAdmin(Context context) {
        return GeofenceContract.Users.ROLE_ADMIN.equals(getRole(context));
    }

    public static String currentUsername(Context context) {
        return AuthSession.username();
    }

    public static String getRole(Context context) {
        String username = currentUsername(context);
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(username),
                new String[]{GeofenceContract.Users.ROLE},
                null,
                null,
                null);
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

    private static boolean usernameExists(Context context, String username) {
        Cursor cursor = context.getContentResolver().query(
                GeofenceContract.Users.byUsernameUri(username),
                new String[]{GeofenceContract.Users.USERNAME},
                null,
                null,
                null);
        if (cursor == null) {
            return false;
        }
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private static ContentValues tokenValues(String token) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.AUTH_TOKEN, token);
        return values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
