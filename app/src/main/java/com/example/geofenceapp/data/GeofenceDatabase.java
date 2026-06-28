package com.example.geofenceapp.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GeofenceDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "geofence_app.db";
    private static final int DB_VERSION = 3;

    public GeofenceDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + GeofenceContract.Users.TABLE + " ("
                + GeofenceContract.Users._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Users.USERNAME + " TEXT NOT NULL UNIQUE, "
                + GeofenceContract.Users.PASSWORD_HASH + " TEXT NOT NULL, "
                + GeofenceContract.Users.PASSWORD_SALT + " TEXT NOT NULL, "
                + GeofenceContract.Users.AUTH_TOKEN + " TEXT NOT NULL DEFAULT '', "
                + GeofenceContract.Users.ROLE + " TEXT NOT NULL, "
                + GeofenceContract.Users.CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + GeofenceContract.Sessions.TABLE + " ("
                + GeofenceContract.Sessions._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Sessions.USERNAME + " TEXT NOT NULL, "
                + GeofenceContract.Sessions.STARTED_AT + " INTEGER NOT NULL, "
                + GeofenceContract.Sessions.ENDED_AT + " INTEGER, "
                + GeofenceContract.Sessions.ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                + "FOREIGN KEY(" + GeofenceContract.Sessions.USERNAME + ") REFERENCES "
                + GeofenceContract.Users.TABLE + "(" + GeofenceContract.Users.USERNAME + "))");

        db.execSQL("CREATE TABLE " + GeofenceContract.Areas.TABLE + " ("
                + GeofenceContract.Areas._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Areas.USERNAME + " TEXT NOT NULL, "
                + GeofenceContract.Areas.SESSION_ID + " INTEGER NOT NULL, "
                + GeofenceContract.Areas.LATITUDE + " REAL NOT NULL, "
                + GeofenceContract.Areas.LONGITUDE + " REAL NOT NULL, "
                + GeofenceContract.Areas.RADIUS_METERS + " REAL NOT NULL, "
                + "FOREIGN KEY(" + GeofenceContract.Areas.USERNAME + ") REFERENCES "
                + GeofenceContract.Users.TABLE + "(" + GeofenceContract.Users.USERNAME + "), "
                + "FOREIGN KEY(" + GeofenceContract.Areas.SESSION_ID + ") REFERENCES "
                + GeofenceContract.Sessions.TABLE + "(" + GeofenceContract.Sessions._ID + "))");

        db.execSQL("CREATE TABLE " + GeofenceContract.Transitions.TABLE + " ("
                + GeofenceContract.Transitions._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Transitions.USERNAME + " TEXT NOT NULL, "
                + GeofenceContract.Transitions.SESSION_ID + " INTEGER NOT NULL, "
                + GeofenceContract.Transitions.AREA_ID + " INTEGER NOT NULL, "
                + GeofenceContract.Transitions.LATITUDE + " REAL NOT NULL, "
                + GeofenceContract.Transitions.LONGITUDE + " REAL NOT NULL, "
                + GeofenceContract.Transitions.TYPE + " TEXT NOT NULL, "
                + GeofenceContract.Transitions.CREATED_AT + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + GeofenceContract.Transitions.USERNAME + ") REFERENCES "
                + GeofenceContract.Users.TABLE + "(" + GeofenceContract.Users.USERNAME + "), "
                + "FOREIGN KEY(" + GeofenceContract.Transitions.SESSION_ID + ") REFERENCES "
                + GeofenceContract.Sessions.TABLE + "(" + GeofenceContract.Sessions._ID + "), "
                + "FOREIGN KEY(" + GeofenceContract.Transitions.AREA_ID + ") REFERENCES "
                + GeofenceContract.Areas.TABLE + "(" + GeofenceContract.Areas._ID + "))");

        db.execSQL("CREATE TABLE " + GeofenceContract.Pins.TABLE + " ("
                + GeofenceContract.Pins._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Pins.USERNAME + " TEXT NOT NULL, "
                + GeofenceContract.Pins.LABEL + " TEXT NOT NULL, "
                + GeofenceContract.Pins.LATITUDE + " REAL NOT NULL, "
                + GeofenceContract.Pins.LONGITUDE + " REAL NOT NULL, "
                + GeofenceContract.Pins.RADIUS_METERS + " REAL NOT NULL, "
                + GeofenceContract.Pins.ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                + GeofenceContract.Pins.CREATED_AT + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + GeofenceContract.Pins.USERNAME + ") REFERENCES "
                + GeofenceContract.Users.TABLE + "(" + GeofenceContract.Users.USERNAME + "))");

        seedAdmin(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + GeofenceContract.Users.TABLE + " ("
                    + GeofenceContract.Users._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + GeofenceContract.Users.USERNAME + " TEXT NOT NULL UNIQUE, "
                    + GeofenceContract.Users.PASSWORD_HASH + " TEXT NOT NULL, "
                    + GeofenceContract.Users.PASSWORD_SALT + " TEXT NOT NULL, "
                    + GeofenceContract.Users.AUTH_TOKEN + " TEXT NOT NULL DEFAULT '', "
                    + GeofenceContract.Users.ROLE + " TEXT NOT NULL, "
                    + GeofenceContract.Users.CREATED_AT + " INTEGER NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + GeofenceContract.Pins.TABLE + " ("
                    + GeofenceContract.Pins._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + GeofenceContract.Pins.USERNAME + " TEXT NOT NULL, "
                    + GeofenceContract.Pins.LABEL + " TEXT NOT NULL, "
                    + GeofenceContract.Pins.LATITUDE + " REAL NOT NULL, "
                    + GeofenceContract.Pins.LONGITUDE + " REAL NOT NULL, "
                    + GeofenceContract.Pins.RADIUS_METERS + " REAL NOT NULL, "
                    + GeofenceContract.Pins.ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                    + GeofenceContract.Pins.CREATED_AT + " INTEGER NOT NULL, "
                    + "FOREIGN KEY(" + GeofenceContract.Pins.USERNAME + ") REFERENCES "
                    + GeofenceContract.Users.TABLE + "(" + GeofenceContract.Users.USERNAME + "))");

            addUsernameColumnIfMissing(db, GeofenceContract.Sessions.TABLE);
            addUsernameColumnIfMissing(db, GeofenceContract.Areas.TABLE);
            addUsernameColumnIfMissing(db, GeofenceContract.Transitions.TABLE);

            seedAdmin(db);
        }
        if (oldVersion < 3) {
            seedAdmin(db);
        }
    }

    private void addUsernameColumnIfMissing(SQLiteDatabase db, String table) {
        android.database.Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean hasUsername = false;
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    if ("username".equals(cursor.getString(cursor.getColumnIndex("name")))) {
                        hasUsername = true;
                        break;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        if (!hasUsername) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN username TEXT NOT NULL DEFAULT 'guest'");
        }
    }

    private void seedAdmin(SQLiteDatabase db) {
        android.content.ContentValues values = new android.content.ContentValues();
        String salt = com.example.geofenceapp.util.PasswordHasher.generateSalt();
        values.put(GeofenceContract.Users.USERNAME, com.example.geofenceapp.util.AuthManager.ADMIN_USERNAME);
        values.put(GeofenceContract.Users.PASSWORD_HASH, com.example.geofenceapp.util.PasswordHasher.hashPassword(com.example.geofenceapp.util.AuthManager.ADMIN_PASSWORD, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.AUTH_TOKEN, "");
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_ADMIN);
        values.put(GeofenceContract.Users.CREATED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(GeofenceContract.Users.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }
}
