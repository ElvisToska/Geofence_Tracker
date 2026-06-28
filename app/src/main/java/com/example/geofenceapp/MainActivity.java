package com.example.geofenceapp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.location.ServiceStarter;
import com.example.geofenceapp.util.AuthManager;
import com.example.geofenceapp.util.AppPrefs;
import com.example.geofenceapp.util.PermissionUtils;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView authStateText;
    private Button loginButton;
    private Button signupButton;
    private Button logoutButton;
    private Button adminButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        authStateText = findViewById(R.id.authStateText);
        AuthManager.ensureSeedAdmin(this);
        AuthManager.restoreSession(this);

        if (!PermissionUtils.hasFineLocation(this)) {
            PermissionUtils.requestForegroundLocation(this);
        }

        loginButton = findViewById(R.id.loginButton);
        signupButton = findViewById(R.id.signupButton);
        logoutButton = findViewById(R.id.logoutButton);
        adminButton = findViewById(R.id.adminButton);

        updateStatusText();
        updateAuthState();
        updateButtonVisibility();

        findViewById(R.id.openMapButton).setOnClickListener(v ->
                requireLoginThen(() -> startActivity(new Intent(this, MapActivity.class))));
        findViewById(R.id.openResultsButton).setOnClickListener(v ->
                requireLoginThen(() -> startActivity(new Intent(this, ResultsMapActivity.class))));
        findViewById(R.id.stopTrackingButton).setOnClickListener(this::stopTracking);
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
        adminButton.setOnClickListener(v ->
                requireAdminThen(() -> startActivity(new Intent(this, AdminActivity.class))));
    }

    private void stopTracking(View view) {
        AppPrefs.setServiceEnabled(this, false);
        ServiceStarter.stop(this);

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

    private void requireLoginThen(Runnable action) {
        if (!AppPrefs.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        action.run();
    }

    private void requireAdminThen(Runnable action) {
        if (!AuthManager.isAdmin(this)) {
            updateAuthState();
            return;
        }
        action.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AuthManager.restoreSession(this);
        updateStatusText();
        updateAuthState();
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        boolean loggedIn = AppPrefs.isLoggedIn(this);
        loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        signupButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        adminButton.setVisibility(loggedIn && AuthManager.isAdmin(this) ? View.VISIBLE : View.GONE);
    }

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
