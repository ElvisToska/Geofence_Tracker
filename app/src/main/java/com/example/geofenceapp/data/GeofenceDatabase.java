package com.example.geofenceapp.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GeofenceDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "geofence_app.db";
    private static final int DB_VERSION = 1;

    public GeofenceDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + GeofenceContract.Sessions.TABLE + " ("
                + GeofenceContract.Sessions._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Sessions.STARTED_AT + " INTEGER NOT NULL, "
                + GeofenceContract.Sessions.ENDED_AT + " INTEGER, "
                + GeofenceContract.Sessions.ACTIVE + " INTEGER NOT NULL DEFAULT 1)");

        db.execSQL("CREATE TABLE " + GeofenceContract.Areas.TABLE + " ("
                + GeofenceContract.Areas._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Areas.SESSION_ID + " INTEGER NOT NULL, "
                + GeofenceContract.Areas.LATITUDE + " REAL NOT NULL, "
                + GeofenceContract.Areas.LONGITUDE + " REAL NOT NULL, "
                + GeofenceContract.Areas.RADIUS_METERS + " REAL NOT NULL, "
                + "FOREIGN KEY(" + GeofenceContract.Areas.SESSION_ID + ") REFERENCES "
                + GeofenceContract.Sessions.TABLE + "(" + GeofenceContract.Sessions._ID + "))");

        db.execSQL("CREATE TABLE " + GeofenceContract.Transitions.TABLE + " ("
                + GeofenceContract.Transitions._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + GeofenceContract.Transitions.SESSION_ID + " INTEGER NOT NULL, "
                + GeofenceContract.Transitions.AREA_ID + " INTEGER NOT NULL, "
                + GeofenceContract.Transitions.LATITUDE + " REAL NOT NULL, "
                + GeofenceContract.Transitions.LONGITUDE + " REAL NOT NULL, "
                + GeofenceContract.Transitions.TYPE + " TEXT NOT NULL, "
                + GeofenceContract.Transitions.CREATED_AT + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + GeofenceContract.Transitions.SESSION_ID + ") REFERENCES "
                + GeofenceContract.Sessions.TABLE + "(" + GeofenceContract.Sessions._ID + "), "
                + "FOREIGN KEY(" + GeofenceContract.Transitions.AREA_ID + ") REFERENCES "
                + GeofenceContract.Areas.TABLE + "(" + GeofenceContract.Areas._ID + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + GeofenceContract.Transitions.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + GeofenceContract.Areas.TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + GeofenceContract.Sessions.TABLE);
        onCreate(db);
    }
}
