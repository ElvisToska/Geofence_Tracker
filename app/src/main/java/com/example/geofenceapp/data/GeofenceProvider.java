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

public class GeofenceProvider extends ContentProvider {
    private static final int SESSIONS = 1;
    private static final int AREAS = 2;
    private static final int CURRENT_AREAS = 3;
    private static final int TRANSITIONS = 4;
    private static final int LAST_TRANSITIONS = 5;
    private static final int LAST_AREAS = 6;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Sessions.PATH, SESSIONS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Areas.PATH, AREAS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Areas.PATH + "/" + GeofenceContract.Areas.PATH_CURRENT, CURRENT_AREAS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Areas.PATH + "/" + GeofenceContract.Areas.PATH_LAST, LAST_AREAS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Transitions.PATH, TRANSITIONS);
        MATCHER.addURI(GeofenceContract.AUTHORITY, GeofenceContract.Transitions.PATH + "/" + GeofenceContract.Transitions.PATH_LAST, LAST_TRANSITIONS);
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
        switch (MATCHER.match(uri)) {
            case SESSIONS:
                cursor = db.query(GeofenceContract.Sessions.TABLE, projection, selection, selectionArgs, null, null,
                        sortOrder == null ? GeofenceContract.Sessions._ID + " DESC" : sortOrder);
                break;
            case AREAS:
                cursor = db.query(GeofenceContract.Areas.TABLE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case CURRENT_AREAS:
                cursor = db.rawQuery("SELECT a.* FROM " + GeofenceContract.Areas.TABLE + " a "
                        + "JOIN " + GeofenceContract.Sessions.TABLE + " s ON s." + GeofenceContract.Sessions._ID
                        + " = a." + GeofenceContract.Areas.SESSION_ID
                        + " WHERE s." + GeofenceContract.Sessions.ACTIVE + " = 1"
                        + " ORDER BY a." + GeofenceContract.Areas._ID, null);
                break;
            case LAST_AREAS:
                cursor = db.rawQuery("SELECT a.* FROM " + GeofenceContract.Areas.TABLE + " a "
                        + "WHERE a." + GeofenceContract.Areas.SESSION_ID + " = (SELECT "
                        + GeofenceContract.Sessions._ID + " FROM " + GeofenceContract.Sessions.TABLE
                        + " ORDER BY " + GeofenceContract.Sessions._ID + " DESC LIMIT 1)"
                        + " ORDER BY a." + GeofenceContract.Areas._ID, null);
                break;
            case TRANSITIONS:
                cursor = db.query(GeofenceContract.Transitions.TABLE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case LAST_TRANSITIONS:
                cursor = db.rawQuery("SELECT t.* FROM " + GeofenceContract.Transitions.TABLE + " t "
                        + "WHERE t." + GeofenceContract.Transitions.SESSION_ID + " = (SELECT "
                        + GeofenceContract.Sessions._ID + " FROM " + GeofenceContract.Sessions.TABLE
                        + " ORDER BY " + GeofenceContract.Sessions._ID + " DESC LIMIT 1)"
                        + " ORDER BY t." + GeofenceContract.Transitions.CREATED_AT, null);
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
        switch (MATCHER.match(uri)) {
            case SESSIONS:
                ContentValues sessionValues = values == null ? new ContentValues() : new ContentValues(values);
                sessionValues.put(GeofenceContract.Sessions.STARTED_AT, System.currentTimeMillis());
                sessionValues.put(GeofenceContract.Sessions.ACTIVE, 1);
                db.update(GeofenceContract.Sessions.TABLE, stopValues(), GeofenceContract.Sessions.ACTIVE + "=1", null);
                id = db.insertOrThrow(GeofenceContract.Sessions.TABLE, null, sessionValues);
                result = ContentUris.withAppendedId(GeofenceContract.Sessions.URI, id);
                break;
            case AREAS:
                id = db.insertOrThrow(GeofenceContract.Areas.TABLE, null, values);
                result = ContentUris.withAppendedId(GeofenceContract.Areas.URI, id);
                break;
            case TRANSITIONS:
                ContentValues transitionValues = values == null ? new ContentValues() : new ContentValues(values);
                transitionValues.put(GeofenceContract.Transitions.CREATED_AT, System.currentTimeMillis());
                id = db.insertOrThrow(GeofenceContract.Transitions.TABLE, null, transitionValues);
                result = ContentUris.withAppendedId(GeofenceContract.Transitions.URI, id);
                break;
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
            default:
                throw new IllegalArgumentException("Update not supported: " + uri);
        }
        notify(uri);
        return rows;
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
}
