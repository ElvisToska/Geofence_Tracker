package com.example.geofenceapp.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database helper that creates and migrates the app's database.
 *
 * Schema overview (5 tables):
 * - users:       user accounts with hashed passwords and roles
 * - sessions:    tracking sessions (start/end time, active flag)
 * - areas:       circular geofence zones defined per session
 * - transitions: enter/exit events recorded during tracking
 * - pins:        admin-assigned geofence points per user
 *
 * The database version is incremented with each schema change.
 * The onUpgrade() method uses ALTER TABLE migrations to preserve existing data
 * (it does NOT drop and recreate tables).
 */
public class GeofenceDatabase extends SQLiteOpenHelper {

    private static final String TAG = "GeofenceDatabase";

    /** Database file name stored in the app's private directory. */
    private static final String DB_NAME = "geofence_app.db";

    /**
     * Current database version.
     * Version history:
     *   1 = initial schema (sessions, areas, transitions)
     *   2 = added users table, pins table, username columns on existing tables
     *   3 = re-seed admin account (ensures admin exists after migrations)
     */
    private static final int DB_VERSION = 3;

    public GeofenceDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * Called when the database is created for the first time.
     * Creates all five tables and inserts the seed admin account.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database version " + DB_VERSION);

        // Users table — stores accounts with hashed passwords
        db.execSQL("CREATE TABLE " + GeofenceContract.Users.TABLE + " ("
                + GeofenceContract.Users._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Users.USERNAME + " TEXT NOT NULL UNIQUE, "
                + GeofenceContract.Users.PASSWORD_HASH + " TEXT NOT NULL, "
                + GeofenceContract.Users.PASSWORD_SALT + " TEXT NOT NULL, "
                + GeofenceContract.Users.AUTH_TOKEN + " TEXT NOT NULL DEFAULT '', "
                + GeofenceContract.Users.ROLE + " TEXT NOT NULL, "
                + GeofenceContract.Users.CREATED_AT + " INTEGER NOT NULL)");

        // Sessions table — one row per tracking session
        db.execSQL("CREATE TABLE " + GeofenceContract.Sessions.TABLE + " ("
                + GeofenceContract.Sessions._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Sessions.USERNAME + " TEXT NOT NULL, "
                + GeofenceContract.Sessions.STARTED_AT + " INTEGER NOT NULL, "
                + GeofenceContract.Sessions.ENDED_AT + " INTEGER, "
                + GeofenceContract.Sessions.ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                + "FOREIGN KEY(" + GeofenceContract.Sessions.USERNAME + ") REFERENCES "
                + GeofenceContract.Users.TABLE + "(" + GeofenceContract.Users.USERNAME + "))");

        // Areas table — circular geofence zones linked to a session
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

        // Transitions table — enter/exit events with GPS coordinates
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

        // Pins table — admin-assigned geofence points per user
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

        // Insert the default admin account
        seedAdmin(db);
    }

    /**
     * Called when the database needs to be upgraded from an older version.
     * Uses ALTER TABLE to add new columns/tables without losing existing data.
     *
     * @param db         the database being upgraded
     * @param oldVersion the version the user currently has
     * @param newVersion the version we are upgrading to
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // Migration from v1 to v2: add users, pins tables and username columns
        if (oldVersion < 2) {
            Log.i(TAG, "Applying migration v1 -> v2: adding users and pins tables");

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

            // Add username column to existing tables that didn't have it
            addUsernameColumnIfMissing(db, GeofenceContract.Sessions.TABLE);
            addUsernameColumnIfMissing(db, GeofenceContract.Areas.TABLE);
            addUsernameColumnIfMissing(db, GeofenceContract.Transitions.TABLE);

            seedAdmin(db);
        }

        // Migration from v2 to v3: re-seed admin (safety net)
        if (oldVersion < 3) {
            Log.i(TAG, "Applying migration v2 -> v3: ensuring admin account exists");
            seedAdmin(db);
        }
    }

    /**
     * Adds a "username" column to the given table if it doesn't already have one.
     * Uses PRAGMA table_info() to inspect the current schema.
     * Existing rows get the default value "guest".
     *
     * @param db    the database instance
     * @param table the table name to modify
     */
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
            Log.i(TAG, "Added username column to table: " + table);
        }
    }

    /**
     * Inserts the default admin account into the users table.
     * Uses CONFLICT_IGNORE so it does nothing if the admin already exists.
     *
     * @param db the database instance
     */
    private void seedAdmin(SQLiteDatabase db) {
        android.content.ContentValues values = new android.content.ContentValues();
        String salt = com.example.geofenceapp.util.PasswordHasher.generateSalt();
        values.put(GeofenceContract.Users.USERNAME, com.example.geofenceapp.util.AuthManager.ADMIN_USERNAME);
        values.put(GeofenceContract.Users.PASSWORD_HASH, com.example.geofenceapp.util.PasswordHasher.hashPassword(com.example.geofenceapp.util.AuthManager.ADMIN_PASSWORD, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.AUTH_TOKEN, "");
        values.put(GeofenceContract.Users.ROLE, GeofenceContract.Users.ROLE_ADMIN);
        values.put(GeofenceContract.Users.CREATED_AT, System.currentTimeMillis());
        long id = db.insertWithOnConflict(GeofenceContract.Users.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) {
            Log.i(TAG, "Seed admin account inserted");
        }
    }
}
