package com.example.geofenceapp.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.geofenceapp.util.AuthSession;

/**
 * ContentProvider that serves as the single data access layer for the app.
 *
 * All queries and inserts are automatically scoped to the current user
 * (via AuthSession.username()), so each user only sees their own data.
 * The provider handles these URI patterns:
 *
 *   content://...provider/sessions          — all sessions for current user
 *   content://...provider/areas             — all areas for current user
 *   content://...provider/areas/current     — areas in the currently active session
 *   content://...provider/areas/last        — areas in the most recent session
 *   content://...provider/transitions       — all transitions for current user
 *   content://...provider/transitions/last  — transitions from the most recent session
 *   content://...provider/users             — all user accounts (admin use)
 *   content://...provider/users/{username}  — a specific user by username
 *   content://...provider/pins              — all pins for current user
 *   content://...provider/pins/{username}   — pins for a specific user
 */
public class GeofenceProvider extends ContentProvider {

    private static final String TAG = "GeofenceProvider";

    // URI match codes — each represents a different query pattern
    private static final int SESSIONS = 1;
    private static final int AREAS = 2;
    private static final int CURRENT_AREAS = 3;
    private static final int TRANSITIONS = 4;
    private static final int LAST_TRANSITIONS = 5;
    private static final int LAST_AREAS = 6;
    private static final int USERS = 7;
    private static final int USER_BY_NAME = 8;
    private static final int PINS = 9;
    private static final int PINS_BY_USER = 10;

    /** Maps incoming URIs to the match codes above. */
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Sessions.PATH, SESSIONS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Areas.PATH, AREAS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Areas.PATH + "/" + GeofenceContract.Areas.PATH_CURRENT, CURRENT_AREAS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Areas.PATH + "/" + GeofenceContract.Areas.PATH_LAST, LAST_AREAS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Transitions.PATH, TRANSITIONS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Transitions.PATH + "/" + GeofenceContract.Transitions.PATH_LAST, LAST_TRANSITIONS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Users.PATH, USERS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Users.PATH + "/*", USER_BY_NAME);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Pins.PATH, PINS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Pins.PATH + "/*", PINS_BY_USER);
    }

    /** The database helper instance. */
    private GeofenceDatabase database;

    @Override
    public boolean onCreate() {
        database = new GeofenceDatabase(getContext());
        Log.i(TAG, "ContentProvider created");
        return true;
    }

    /**
     * Queries the database based on the URI pattern.
     * Most queries are automatically filtered by the current user's username
     * to enforce per-user data isolation.
     */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = database.getReadableDatabase();
        Cursor cursor;
        String username = currentUser();

        switch (MATCHER.match(uri)) {

            // All sessions for the current user, newest first
            case SESSIONS:
                cursor = db.query(GeofenceContract.Sessions.TABLE, projection,
                        ownerSelection(selection, GeofenceContract.Sessions.USERNAME, username),
                        selectionArgs, null, null,
                        sortOrder == null ? GeofenceContract.Sessions._ID + " DESC" : sortOrder);
                break;

            // All areas for the current user
            case AREAS:
                cursor = db.query(GeofenceContract.Areas.TABLE, projection,
                        ownerSelection(selection, GeofenceContract.Areas.USERNAME, username),
                        selectionArgs, null, null, sortOrder);
                break;

            // Areas in the currently active session (where session.active = 1)
            case CURRENT_AREAS:
                cursor = db.rawQuery("SELECT a.* FROM " + GeofenceContract.Areas.TABLE + " a "
                        + "JOIN " + GeofenceContract.Sessions.TABLE + " s ON s." + GeofenceContract.Sessions._ID
                        + " = a." + GeofenceContract.Areas.SESSION_ID
                        + " WHERE s." + GeofenceContract.Sessions.ACTIVE + " = 1"
                        + " AND s." + GeofenceContract.Sessions.USERNAME + " = ?"
                        + " ORDER BY a." + GeofenceContract.Areas._ID,
                        new String[]{username});
                break;

            // Areas in the most recent session (for the results screen)
            case LAST_AREAS:
                cursor = db.rawQuery("SELECT a.* FROM " + GeofenceContract.Areas.TABLE + " a "
                        + "WHERE a." + GeofenceContract.Areas.USERNAME + " = ?"
                        + " AND a." + GeofenceContract.Areas.SESSION_ID + " = (SELECT "
                        + GeofenceContract.Sessions._ID + " FROM " + GeofenceContract.Sessions.TABLE
                        + " WHERE " + GeofenceContract.Sessions.USERNAME + " = ?"
                        + " ORDER BY " + GeofenceContract.Sessions._ID + " DESC LIMIT 1)"
                        + " ORDER BY a." + GeofenceContract.Areas._ID,
                        new String[]{username, username});
                break;

            // All transitions for the current user
            case TRANSITIONS:
                cursor = db.query(GeofenceContract.Transitions.TABLE, projection,
                        ownerSelection(selection, GeofenceContract.Transitions.USERNAME, username),
                        selectionArgs, null, null, sortOrder);
                break;

            // Transitions from the most recent session (for the results screen)
            case LAST_TRANSITIONS:
                cursor = db.rawQuery("SELECT t.* FROM " + GeofenceContract.Transitions.TABLE + " t "
                        + "WHERE t." + GeofenceContract.Transitions.USERNAME + " = ?"
                        + " AND t." + GeofenceContract.Transitions.SESSION_ID + " = (SELECT "
                        + GeofenceContract.Sessions._ID + " FROM " + GeofenceContract.Sessions.TABLE
                        + " WHERE " + GeofenceContract.Sessions.USERNAME + " = ?"
                        + " ORDER BY " + GeofenceContract.Sessions._ID + " DESC LIMIT 1)"
                        + " ORDER BY t." + GeofenceContract.Transitions.CREATED_AT,
                        new String[]{username, username});
                break;

            // All users (used by the admin panel to list accounts)
            case USERS:
                cursor = db.query(GeofenceContract.Users.TABLE, projection, selection, selectionArgs,
                        null, null,
                        sortOrder == null ? GeofenceContract.Users.USERNAME + " ASC" : sortOrder);
                break;

            // A specific user by username (used for login, role checks, etc.)
            case USER_BY_NAME:
                cursor = db.query(GeofenceContract.Users.TABLE, projection,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()},
                        null, null, null);
                break;

            // All pins for the current user
            case PINS:
                cursor = db.query(GeofenceContract.Pins.TABLE, projection,
                        ownerSelection(selection, GeofenceContract.Pins.USERNAME, username),
                        selectionArgs, null, null, sortOrder);
                break;

            // Pins for a specific user (used by admin panel)
            case PINS_BY_USER:
                cursor = db.query(GeofenceContract.Pins.TABLE, projection,
                        GeofenceContract.Pins.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()},
                        null, null, sortOrder);
                break;

            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }

        // Register for content change notifications
        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    /**
     * Inserts a new row into the appropriate table.
     * Automatically sets the username, timestamps, and other defaults.
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase db = database.getWritableDatabase();
        long id;
        Uri result;
        String username = currentUser();

        switch (MATCHER.match(uri)) {

            // Insert a new session — also deactivates any previously active session
            case SESSIONS: {
                ContentValues sessionValues = values == null ? new ContentValues() : new ContentValues(values);
                sessionValues.put(GeofenceContract.Sessions.USERNAME, ownerFromValues(sessionValues, username));
                sessionValues.put(GeofenceContract.Sessions.STARTED_AT, System.currentTimeMillis());
                sessionValues.put(GeofenceContract.Sessions.ACTIVE, 1);
                // Deactivate all currently active sessions before creating a new one
                db.update(GeofenceContract.Sessions.TABLE, stopValues(), GeofenceContract.Sessions.ACTIVE + "=1", null);
                id = db.insertOrThrow(GeofenceContract.Sessions.TABLE, null, sessionValues);
                result = ContentUris.withAppendedId(GeofenceContract.Sessions.URI, id);
                Log.d(TAG, "New session created: id=" + id + " user=" + username);
                break;
            }

            // Insert a new geofence area linked to a session
            case AREAS: {
                ContentValues areaValues = values == null ? new ContentValues() : new ContentValues(values);
                areaValues.put(GeofenceContract.Areas.USERNAME, ownerFromValues(areaValues, username));
                id = db.insertOrThrow(GeofenceContract.Areas.TABLE, null, areaValues);
                result = ContentUris.withAppendedId(GeofenceContract.Areas.URI, id);
                Log.d(TAG, "New area created: id=" + id);
                break;
            }

            // Insert a new transition (enter or exit event)
            case TRANSITIONS: {
                ContentValues transitionValues = values == null ? new ContentValues() : new ContentValues(values);
                transitionValues.put(GeofenceContract.Transitions.USERNAME, ownerFromValues(transitionValues, username));
                transitionValues.put(GeofenceContract.Transitions.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Transitions.TABLE, null, transitionValues);
                result = ContentUris.withAppendedId(GeofenceContract.Transitions.URI, id);
                String type = transitionValues.getAsString(GeofenceContract.Transitions.TYPE);
                Log.i(TAG, "Transition recorded: " + type + " id=" + id + " user=" + username);
                break;
            }

            // Insert a new user account
            case USERS: {
                ContentValues userValues = values == null ? new ContentValues() : new ContentValues(values);
                userValues.put(GeofenceContract.Users.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Users.TABLE, null, userValues);
                result = ContentUris.withAppendedId(GeofenceContract.Users.URI, id);
                Log.i(TAG, "New user created: id=" + id);
                break;
            }

            // Insert a new pin (admin-assigned geofence point)
            case PINS: {
                ContentValues pinValues = values == null ? new ContentValues() : new ContentValues(values);
                pinValues.put(GeofenceContract.Pins.USERNAME, ownerFromValues(pinValues, username));
                pinValues.put(GeofenceContract.Pins.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Pins.TABLE, null, pinValues);
                result = ContentUris.withAppendedId(GeofenceContract.Pins.URI, id);
                Log.d(TAG, "New pin created: id=" + id);
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }

        notify(uri);
        return result;
    }

    /** Deletes rows matching the given URI and selection criteria. */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = database.getWritableDatabase();
        int rows;

        switch (MATCHER.match(uri)) {
            case AREAS:
                rows = db.delete(GeofenceContract.Areas.TABLE, selection, selectionArgs);
                break;

            case TRANSITIONS:
                rows = db.delete(GeofenceContract.Transitions.TABLE, selection, selectionArgs);
                break;

            // Delete a user by the username in the URI path
            case USERS:
            case USER_BY_NAME:
                rows = db.delete(GeofenceContract.Users.TABLE,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()});
                if (rows > 0) {
                    Log.i(TAG, "User deleted: " + uri.getLastPathSegment());
                }
                break;

            // Delete pins scoped to a specific user, with optional extra selection
            case PINS:
            case PINS_BY_USER:
                rows = db.delete(GeofenceContract.Pins.TABLE,
                        GeofenceContract.Pins.USERNAME + " = ?" + (selection == null ? "" : " AND (" + selection + ")"),
                        concatArgs(uri.getLastPathSegment(), selectionArgs));
                break;

            default:
                throw new IllegalArgumentException("Delete not supported: " + uri);
        }

        notify(uri);
        return rows;
    }

    /** Updates rows matching the given URI and selection criteria. */
    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = database.getWritableDatabase();
        int rows;

        switch (MATCHER.match(uri)) {
            case SESSIONS:
                rows = db.update(GeofenceContract.Sessions.TABLE, values, selection, selectionArgs);
                break;

            // Update a user by the username in the URI path
            case USERS:
            case USER_BY_NAME:
                rows = db.update(GeofenceContract.Users.TABLE, values,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;

            // Update pins scoped to a specific user
            case PINS:
            case PINS_BY_USER:
                rows = db.update(GeofenceContract.Pins.TABLE, values,
                        GeofenceContract.Pins.USERNAME + " = ?" + (selection == null ? "" : " AND (" + selection + ")"),
                        concatArgs(uri.getLastPathSegment(), selectionArgs));
                break;

            default:
                throw new IllegalArgumentException("Update not supported: " + uri);
        }

        notify(uri);
        return rows;
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /** Returns the current user's username from the in-memory AuthSession. */
    private String currentUser() {
        return AuthSession.username();
    }

    /**
     * Extracts the username from ContentValues if present, otherwise falls back
     * to the given default (typically the current logged-in user).
     */
    private String ownerFromValues(ContentValues values, String fallback) {
        String owner = values.getAsString(GeofenceContract.Users.USERNAME);
        if (owner == null) {
            owner = values.getAsString(GeofenceContract.Sessions.USERNAME);
        }
        if (owner == null) {
            owner = values.getAsString(GeofenceContract.Areas.USERNAME);
        }
        if (owner == null) {
            owner = values.getAsString(GeofenceContract.Transitions.USERNAME);
        }
        if (owner == null) {
            owner = values.getAsString(GeofenceContract.Pins.USERNAME);
        }
        return owner == null ? fallback : owner;
    }

    /**
     * Builds a WHERE clause that filters rows by the current user's username.
     * If an existing selection is provided, it's combined with AND.
     *
     * @param selection existing WHERE clause (may be null)
     * @param column    the username column name
     * @param username  the current user's username
     * @return the combined WHERE clause
     */
    private String ownerSelection(@Nullable String selection, String column, String username) {
        if (selection == null || selection.trim().isEmpty()) {
            return column + " = '" + username + "'";
        }
        return column + " = '" + username + "' AND (" + selection + ")";
    }

    /** Creates ContentValues to mark a session as stopped (active=0, ended_at=now). */
    private ContentValues stopValues() {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Sessions.ACTIVE, 0);
        values.put(GeofenceContract.Sessions.ENDED_AT, System.currentTimeMillis());
        return values;
    }

    /** Notifies observers that data at the given URI has changed. */
    private void notify(Uri uri) {
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

    /**
     * Prepends a value to an array of selection args.
     * Used when the URI path contains one parameter (e.g., username)
     * and the caller provides additional selection args.
     */
    private String[] concatArgs(String first, @Nullable String[] rest) {
        if (rest == null || rest.length == 0) {
            return new String[]{first};
        }
        String[] args = new String[rest.length + 1];
        args[0] = first;
        System.arraycopy(rest, 0, args, 1, rest.length);
        return args;
    }
}
