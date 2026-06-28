package com.example.geofenceapp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.location.ServiceStarter;
import com.example.geofenceapp.util.AuthManager;
import com.example.geofenceapp.util.AppPrefs;
import com.example.geofenceapp.util.PermissionUtils;

/**
 * Main entry point of the application.
 *
 * This screen shows the current auth state, tracking status, and provides
 * navigation to all other screens. It handles:
 * - Seeding the admin account on first launch
 * - Restoring the user session after a process restart
 * - Toggling button visibility based on login state (guest vs user vs admin)
 * - Requesting location permission if not already granted
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    /** Displays the current tracking status (e.g., "Ready", "Tracking", "Not signed in"). */
    private TextView statusText;

    /** Displays who is currently logged in (e.g., "Signed in as admin"). */
    private TextView authStateText;

    /** Auth-related buttons whose visibility changes based on login state. */
    private Button loginButton;
    private Button signupButton;
    private Button logoutButton;
    private Button adminButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        // Bind UI elements
        statusText = findViewById(R.id.statusText);
        authStateText = findViewById(R.id.authStateText);

        // Ensure the default admin account exists in the database
        AuthManager.ensureSeedAdmin(this);

        // Restore the in-memory session from SharedPreferences (handles process restarts)
        AuthManager.restoreSession(this);

        // Request location permission if not already granted
        if (!PermissionUtils.hasFineLocation(this)) {
            PermissionUtils.requestForegroundLocation(this);
        }

        // Bind auth buttons
        loginButton = findViewById(R.id.loginButton);
        signupButton = findViewById(R.id.signupButton);
        logoutButton = findViewById(R.id.logoutButton);
        adminButton = findViewById(R.id.adminButton);

        // Refresh all UI elements to reflect current state
        updateStatusText();
        updateAuthState();
        updateButtonVisibility();

        // Navigation: open map to define geofence areas (requires login)
        findViewById(R.id.openMapButton).setOnClickListener(v ->
                requireLoginThen(() -> startActivity(new Intent(this, MapActivity.class))));

        // Navigation: open results screen (requires login)
        findViewById(R.id.openResultsButton).setOnClickListener(v ->
                requireLoginThen(() -> startActivity(new Intent(this, ResultsMapActivity.class))));

        // Stop the tracking service and end all active sessions
        findViewById(R.id.stopTrackingButton).setOnClickListener(this::stopTracking);

        // Auth buttons
        loginButton.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        signupButton.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
        logoutButton.setOnClickListener(v -> {
            AuthManager.logout(this);
            updateAuthState();
            updateStatusText();
            updateButtonVisibility();
        });

        // Admin panel (only accessible when the user has the admin role)
        adminButton.setOnClickListener(v ->
                requireAdminThen(() -> startActivity(new Intent(this, AdminActivity.class))));
    }

    /**
     * Stops the geofence tracking service and marks all active sessions as ended.
     */
    private void stopTracking(View view) {
        Log.i(TAG, "Stopping tracking service");
        AppPrefs.setServiceEnabled(this, false);
        ServiceStarter.stop(this);

        // Mark all active sessions as ended in the database
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Sessions.ACTIVE, 0);
        values.put(GeofenceContract.Sessions.ENDED_AT, System.currentTimeMillis());
        getContentResolver().update(
                GeofenceContract.Sessions.URI,
                values,
                GeofenceContract.Sessions.ACTIVE + "=1",
                null);
        updateStatusText();
    }

    /**
     * Guards an action behind a login check.
     * If the user is not logged in, redirects to the login screen instead.
     */
    private void requireLoginThen(Runnable action) {
        if (!AppPrefs.isLoggedIn(this)) {
            Log.d(TAG, "Action requires login — redirecting to LoginActivity");
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        action.run();
    }

    /**
     * Guards an action behind an admin role check.
     * If the user is not an admin, the action is silently blocked.
     */
    private void requireAdminThen(Runnable action) {
        if (!AuthManager.isAdmin(this)) {
            Log.d(TAG, "Action requires admin role — blocked");
            updateAuthState();
            return;
        }
        action.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check everything when returning from login/signup/admin screens
        AuthManager.restoreSession(this);
        updateStatusText();
        updateAuthState();
        updateButtonVisibility();
    }

    /**
     * Shows or hides auth buttons based on whether the user is logged in.
     * - Guest: shows Login + Sign Up, hides Logout + Admin
     * - Regular user: shows Logout, hides Login + Sign Up + Admin
     * - Admin: shows Logout + Admin Panel, hides Login + Sign Up
     */
    private void updateButtonVisibility() {
        boolean loggedIn = AppPrefs.isLoggedIn(this);
        loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        signupButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        adminButton.setVisibility(loggedIn && AuthManager.isAdmin(this) ? View.VISIBLE : View.GONE);
    }

    /** Updates the status text based on auth state, permission state, and tracking state. */
    private void updateStatusText() {
        if (statusText == null) {
            return;
        }
        if (!AppPrefs.isLoggedIn(this)) {
            statusText.setText(R.string.main_auth_guest);
        } else if (!PermissionUtils.hasFineLocation(this)) {
            statusText.setText(R.string.main_status_needs_location);
        } else if (AppPrefs.isServiceEnabled(this)) {
            statusText.setText(R.string.main_status_tracking);
        } else {
            statusText.setText(R.string.main_status_ready);
        }
    }

    /** Updates the auth state label to show who is currently logged in. */
    private void updateAuthState() {
        if (authStateText == null) {
            return;
        }
        if (!AppPrefs.isLoggedIn(this)) {
            authStateText.setText(R.string.main_auth_guest);
        } else if (AuthManager.isAdmin(this)) {
            authStateText.setText(R.string.main_auth_admin);
        } else {
            authStateText.setText(getString(R.string.main_auth_user, AuthManager.currentUsername(this)));
        }
    }
}
