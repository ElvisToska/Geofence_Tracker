package com.example.geofenceapp.data;

import android.net.Uri;
import android.provider.BaseColumns;

public final class GeofenceContract {
    public static final String AUTHORITY = "com.example.geofenceapp.provider";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);

    private GeofenceContract() {
    }

    public static final class Sessions implements BaseColumns {
        public static final String PATH = "sessions";
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();
        public static final String TABLE = "sessions";
        public static final String USERNAME = "username";
        public static final String STARTED_AT = "started_at";
        public static final String ENDED_AT = "ended_at";
        public static final String ACTIVE = "active";
    }

    public static final class Areas implements BaseColumns {
        public static final String PATH = "areas";
        public static final String PATH_CURRENT = "current";
        public static final String PATH_LAST = "last";
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();
        public static final Uri CURRENT_URI = BASE_URI.buildUpon().appendPath(PATH).appendPath(PATH_CURRENT).build();
        public static final Uri LAST_URI = BASE_URI.buildUpon().appendPath(PATH).appendPath(PATH_LAST).build();
        public static final String TABLE = "areas";
        public static final String USERNAME = "username";
        public static final String SESSION_ID = "session_id";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String RADIUS_METERS = "radius_meters";
    }

    public static final class Transitions implements BaseColumns {
        public static final String PATH = "transitions";
        public static final String PATH_LAST = "last";
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();
        public static final Uri LAST_URI = BASE_URI.buildUpon().appendPath(PATH).appendPath(PATH_LAST).build();
        public static final String TABLE = "transitions";
        public static final String USERNAME = "username";
        public static final String SESSION_ID = "session_id";
        public static final String AREA_ID = "area_id";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String TYPE = "type";
        public static final String CREATED_AT = "created_at";
        public static final String ENTER = "ENTER";
        public static final String EXIT = "EXIT";
    }

    public static final class Users implements BaseColumns {
        public static final String PATH = "users";
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();
        public static Uri byUsernameUri(String username) {
            return BASE_URI.buildUpon().appendPath(PATH).appendPath(username).build();
        }
        public static final String TABLE = "users";
        public static final String USERNAME = "username";
        public static final String PASSWORD_HASH = "password_hash";
        public static final String PASSWORD_SALT = "password_salt";
        public static final String AUTH_TOKEN = "auth_token";
        public static final String ROLE = "role";
        public static final String CREATED_AT = "created_at";
        public static final String ROLE_ADMIN = "admin";
        public static final String ROLE_USER = "user";
        public static final String ROLE_GUEST = "guest";
    }

    public static final class Pins implements BaseColumns {
        public static final String PATH = "pins";
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();
        public static Uri byUsernameUri(String username) {
            return BASE_URI.buildUpon().appendPath(PATH).appendPath(username).build();
        }
        public static final String TABLE = "pins";
        public static final String USERNAME = "username";
        public static final String LABEL = "label";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String RADIUS_METERS = "radius_meters";
        public static final String ACTIVE = "active";
        public static final String CREATED_AT = "created_at";
    }
}
