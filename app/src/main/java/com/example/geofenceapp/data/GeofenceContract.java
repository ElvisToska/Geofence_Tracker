package com.example.geofenceapp.data;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Contract class that defines the database schema and content URIs
 * for the Geofence Tracker application.
 *
 * Each inner class represents one database table and contains:
 * - Column name constants
 * - Content URIs for querying/inserting via the ContentProvider
 * - Table name constant
 *
 * The ContentProvider authority is "com.example.geofenceapp.provider".
 */
public final class GeofenceContract {

    /** ContentProvider authority — must match the one declared in AndroidManifest.xml. */
    public static final String AUTHORITY = "com.example.geofenceapp.provider";

    /** Base content URI for all provider operations. */
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);

    /** Private constructor — this is a constants-only class. */
    private GeofenceContract() {
    }

    // =========================================================================
    // Sessions table — tracks individual tracking sessions
    // =========================================================================

    /**
     * A tracking session represents one period of geofence monitoring.
     * Each session has a start time, an optional end time, and an active flag.
     * Creating a new session automatically deactivates any previous active session.
     */
    public static final class Sessions implements BaseColumns {
        public static final String PATH = "sessions";
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();
        public static final String TABLE = "sessions";

        /** The username that owns this session (foreign key to users.username). */
        public static final String USERNAME = "username";

        /** Timestamp (millis) when the session started. */
        public static final String STARTED_AT = "started_at";

        /** Timestamp (millis) when the session ended, or null if still active. */
        public static final String ENDED_AT = "ended_at";

        /** 1 if the session is currently active, 0 if ended. */
        public static final String ACTIVE = "active";
    }

    // =========================================================================
    // Areas table — geofence circles defined within a session
    // =========================================================================

    /**
     * An area is a circular geofence zone (lat/lng center + radius in meters).
     * Users define areas on the map, and the tracking service monitors
     * whether the device enters or exits each area.
     */
    public static final class Areas implements BaseColumns {
        public static final String PATH = "areas";
        public static final String PATH_CURRENT = "current";
        public static final String PATH_LAST = "last";

        /** Query all areas for the current user. */
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();

        /** Query areas belonging to the currently active session. */
        public static final Uri CURRENT_URI = BASE_URI.buildUpon().appendPath(PATH).appendPath(PATH_CURRENT).build();

        /** Query areas belonging to the most recent (last) session. */
        public static final Uri LAST_URI = BASE_URI.buildUpon().appendPath(PATH).appendPath(PATH_LAST).build();

        public static final String TABLE = "areas";
        public static final String USERNAME = "username";
        public static final String SESSION_ID = "session_id";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String RADIUS_METERS = "radius_meters";
    }

    // =========================================================================
    // Transitions table — geofence enter/exit events
    // =========================================================================

    /**
     * A transition records a single enter or exit event.
     * Each transition stores the GPS coordinates where the event was detected,
     * linked to the session and area that triggered it.
     */
    public static final class Transitions implements BaseColumns {
        public static final String PATH = "transitions";
        public static final String PATH_LAST = "last";

        /** Query all transitions for the current user. */
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();

        /** Query transitions belonging to the most recent (last) session. */
        public static final Uri LAST_URI = BASE_URI.buildUpon().appendPath(PATH).appendPath(PATH_LAST).build();

        public static final String TABLE = "transitions";
        public static final String USERNAME = "username";
        public static final String SESSION_ID = "session_id";
        public static final String AREA_ID = "area_id";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";

        /** Either "ENTER" or "EXIT". */
        public static final String TYPE = "type";

        /** Timestamp (millis) when the transition was detected. */
        public static final String CREATED_AT = "created_at";

        /** Constant for an entry transition. */
        public static final String ENTER = "ENTER";

        /** Constant for an exit transition. */
        public static final String EXIT = "EXIT";
    }

    // =========================================================================
    // Users table — user accounts and authentication
    // =========================================================================

    /**
     * Stores user accounts with hashed passwords and role-based access.
     * Roles: "admin" (full access, admin panel), "user" (normal user), "guest" (not logged in).
     */
    public static final class Users implements BaseColumns {
        public static final String PATH = "users";

        /** Query all users (admin only). */
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();

        /** Builds a URI to query/update/delete a specific user by username. */
        public static Uri byUsernameUri(String username) {
            return BASE_URI.buildUpon().appendPath(PATH).appendPath(username).build();
        }

        public static final String TABLE = "users";
        public static final String USERNAME = "username";

        /** PBKDF2-hashed password (Base64-encoded). */
        public static final String PASSWORD_HASH = "password_hash";

        /** Per-user random salt used for password hashing (Base64-encoded). */
        public static final String PASSWORD_SALT = "password_salt";

        /** Auth token issued on login, cleared on logout. */
        public static final String AUTH_TOKEN = "auth_token";

        /** User's role: "admin", "user", or "guest". */
        public static final String ROLE = "role";

        /** Timestamp (millis) when the account was created. */
        public static final String CREATED_AT = "created_at";

        public static final String ROLE_ADMIN = "admin";
        public static final String ROLE_USER = "user";
        public static final String ROLE_GUEST = "guest";
    }

    // =========================================================================
    // Pins table — admin-assigned geofence points
    // =========================================================================

    /**
     * Pins are geofence points that an admin assigns to a specific user.
     * Each pin has a label, coordinates, radius, and an active flag.
     */
    public static final class Pins implements BaseColumns {
        public static final String PATH = "pins";

        /** Query all pins for the current user. */
        public static final Uri URI = BASE_URI.buildUpon().appendPath(PATH).build();

        /** Builds a URI to query pins for a specific user by username. */
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
