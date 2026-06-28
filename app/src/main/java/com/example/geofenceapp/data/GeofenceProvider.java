package com.example.geofenceapp.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.geofenceapp.util.AuthSession;

public class GeofenceProvider extends ContentProvider {
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

    private GeofenceDatabase database;

    @Override
    public boolean onCreate() {
        database = new GeofenceDatabase(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = database.getReadableDatabase();
        Cursor cursor;
        String username = currentUser();
        switch (MATCHER.match(uri)) {
            case SESSIONS:
                cursor = db.query(GeofenceContract.Sessions.TABLE, projection, ownerSelection(selection, GeofenceContract.Sessions.USERNAME, username),
                        selectionArgs, null, null, sortOrder == null ? GeofenceContract.Sessions._ID + " DESC" : sortOrder);
                break;
            case AREAS:
                cursor = db.query(GeofenceContract.Areas.TABLE, projection, ownerSelection(selection, GeofenceContract.Areas.USERNAME, username),
                        selectionArgs, null, null, sortOrder);
                break;
            case CURRENT_AREAS:
                cursor = db.rawQuery("SELECT a.* FROM " + GeofenceContract.Areas.TABLE + " a "
                        + "JOIN " + GeofenceContract.Sessions.TABLE + " s ON s." + GeofenceContract.Sessions._ID
                        + " = a." + GeofenceContract.Areas.SESSION_ID
                        + " WHERE s." + GeofenceContract.Sessions.ACTIVE + " = 1"
                        + " AND s." + GeofenceContract.Sessions.USERNAME + " = ?"
                        + " ORDER BY a." + GeofenceContract.Areas._ID,
                        new String[]{username});
                break;
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
            case TRANSITIONS:
                cursor = db.query(GeofenceContract.Transitions.TABLE, projection, ownerSelection(selection, GeofenceContract.Transitions.USERNAME, username),
                        selectionArgs, null, null, sortOrder);
                break;
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
            case USERS:
                cursor = db.query(GeofenceContract.Users.TABLE, projection, selection, selectionArgs, null, null,
                        sortOrder == null ? GeofenceContract.Users.USERNAME + " ASC" : sortOrder);
                break;
            case USER_BY_NAME:
                cursor = db.query(GeofenceContract.Users.TABLE, projection,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()},
                        null, null, null);
                break;
            case PINS:
                cursor = db.query(GeofenceContract.Pins.TABLE, projection, ownerSelection(selection, GeofenceContract.Pins.USERNAME, username),
                        selectionArgs, null, null, sortOrder);
                break;
            case PINS_BY_USER:
                cursor = db.query(GeofenceContract.Pins.TABLE, projection,
                        GeofenceContract.Pins.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()},
                        null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
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

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase db = database.getWritableDatabase();
        long id;
        Uri result;
        String username = currentUser();
        switch (MATCHER.match(uri)) {
            case SESSIONS: {
                ContentValues sessionValues = values == null ? new ContentValues() : new ContentValues(values);
                sessionValues.put(GeofenceContract.Sessions.USERNAME, ownerFromValues(sessionValues, username));
                sessionValues.put(GeofenceContract.Sessions.STARTED_AT, System.currentTimeMillis());
                sessionValues.put(GeofenceContract.Sessions.ACTIVE, 1);
                db.update(GeofenceContract.Sessions.TABLE, stopValues(), GeofenceContract.Sessions.ACTIVE + "=1", null);
                id = db.insertOrThrow(GeofenceContract.Sessions.TABLE, null, sessionValues);
                result = ContentUris.withAppendedId(GeofenceContract.Sessions.URI, id);
                break;
            }
            case AREAS: {
                ContentValues areaValues = values == null ? new ContentValues() : new ContentValues(values);
                areaValues.put(GeofenceContract.Areas.USERNAME, ownerFromValues(areaValues, username));
                id = db.insertOrThrow(GeofenceContract.Areas.TABLE, null, areaValues);
                result = ContentUris.withAppendedId(GeofenceContract.Areas.URI, id);
                break;
            }
            case TRANSITIONS: {
                ContentValues transitionValues = values == null ? new ContentValues() : new ContentValues(values);
                transitionValues.put(GeofenceContract.Transitions.USERNAME, ownerFromValues(transitionValues, username));
                transitionValues.put(GeofenceContract.Transitions.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Transitions.TABLE, null, transitionValues);
                result = ContentUris.withAppendedId(GeofenceContract.Transitions.URI, id);
                break;
            }
            case USERS: {
                ContentValues userValues = values == null ? new ContentValues() : new ContentValues(values);
                userValues.put(GeofenceContract.Users.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Users.TABLE, null, userValues);
                result = ContentUris.withAppendedId(GeofenceContract.Users.URI, id);
                break;
            }
            case PINS: {
                ContentValues pinValues = values == null ? new ContentValues() : new ContentValues(values);
                pinValues.put(GeofenceContract.Pins.USERNAME, ownerFromValues(pinValues, username));
                pinValues.put(GeofenceContract.Pins.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Pins.TABLE, null, pinValues);
                result = ContentUris.withAppendedId(GeofenceContract.Pins.URI, id);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown uri: " + uri);
        }
        notify(uri);
        return result;
    }

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
            case USERS:
                rows = db.delete(GeofenceContract.Users.TABLE,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            case USER_BY_NAME:
                rows = db.delete(GeofenceContract.Users.TABLE,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            case PINS:
                rows = db.delete(GeofenceContract.Pins.TABLE,
                        GeofenceContract.Pins.USERNAME + " = ?" + (selection == null ? "" : " AND (" + selection + ")"),
                        concatArgs(uri.getLastPathSegment(), selectionArgs));
                break;
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

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = database.getWritableDatabase();
        int rows;
        switch (MATCHER.match(uri)) {
            case SESSIONS:
                rows = db.update(GeofenceContract.Sessions.TABLE, values, selection, selectionArgs);
                break;
            case USERS:
                rows = db.update(GeofenceContract.Users.TABLE, values,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            case USER_BY_NAME:
                rows = db.update(GeofenceContract.Users.TABLE, values,
                        GeofenceContract.Users.USERNAME + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            case PINS:
                rows = db.update(GeofenceContract.Pins.TABLE, values,
                        GeofenceContract.Pins.USERNAME + " = ?" + (selection == null ? "" : " AND (" + selection + ")"),
                        concatArgs(uri.getLastPathSegment(), selectionArgs));
                break;
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

    private String currentUser() {
        return AuthSession.username();
    }

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

    private String ownerSelection(@Nullable String selection, String column, String username) {
        if (selection == null || selection.trim().isEmpty()) {
            return column + " = '" + username + "'";
        }
        return column + " = '" + username + "' AND (" + selection + ")";
    }

    private ContentValues stopValues() {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Sessions.ACTIVE, 0);
        values.put(GeofenceContract.Sessions.ENDED_AT, System.currentTimeMillis());
        return values;
    }

    private void notify(Uri uri) {
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

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
