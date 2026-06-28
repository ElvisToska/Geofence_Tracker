package com.example.geofenceapp;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.util.AuthManager;
import com.example.geofenceapp.util.PasswordHasher;

/**
 * Administration panel accessible only to users with the "admin" role.
 *
 * Provides functionality to:
 * - Add new user accounts
 * - Delete user accounts (with a guard preventing admin self-deletion)
 * - Reset a user's password
 * - Add geofence pins assigned to a specific user
 * - Remove geofence pins by label
 * - View all users with their roles and pin counts
 * - View detailed pins for a specific user
 */
public class AdminActivity extends Activity {

    private static final String TAG = "AdminActivity";

    /** Input field for the target username (for user and pin operations). */
    private EditText userNameInput;

    /** Input field for the password (used for add user and reset password). */
    private EditText passwordInput;

    /** Input fields for pin properties. */
    private EditText pinLabelInput;
    private EditText latitudeInput;
    private EditText longitudeInput;
    private EditText radiusInput;

    /** Displays the list of all users with their roles and pin counts. */
    private TextView usersListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        Log.d(TAG, "onCreate");

        // Block non-admin users immediately
        if (!AuthManager.isAdmin(this)) {
            Log.w(TAG, "Non-admin user attempted to access admin panel");
            Toast.makeText(this, "Admin access only", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind input fields
        userNameInput = findViewById(R.id.userNameInput);
        passwordInput = findViewById(R.id.passwordInput);
        pinLabelInput = findViewById(R.id.pinLabelInput);
        latitudeInput = findViewById(R.id.latitudeInput);
        longitudeInput = findViewById(R.id.longitudeInput);
        radiusInput = findViewById(R.id.radiusInput);
        usersListText = findViewById(R.id.usersListText);

        // Wire up button click handlers
        findViewById(R.id.addUserButton).setOnClickListener(v -> addUser());
        findViewById(R.id.deleteUserButton).setOnClickListener(v -> deleteUser());
        findViewById(R.id.resetPasswordButton).setOnClickListener(v -> resetPassword());
        findViewById(R.id.addPinButton).setOnClickListener(v -> addPin());
        findViewById(R.id.removePinButton).setOnClickListener(v -> removePin());

        // Show the initial user list
        refreshUsers();
    }

    /**
     * Creates a new user account using AuthManager.signUp().
     * The username and password come from the input fields.
     */
    private void addUser() {
        String username = userNameInput.getText().toString();
        boolean ok = AuthManager.signUp(this, username, passwordInput.getText().toString());
        Log.i(TAG, "addUser: " + username + " -> " + (ok ? "success" : "failed"));
        Toast.makeText(this, ok ? "User added" : "Could not add user", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    /**
     * Deletes a user account by username.
     * The admin account cannot be deleted (safety guard).
     */
    private void deleteUser() {
        String target = userNameInput.getText().toString().trim().toLowerCase();

        // Prevent the admin from deleting their own account
        if (target.equals(AuthManager.ADMIN_USERNAME)) {
            Log.w(TAG, "deleteUser: blocked attempt to delete admin account");
            Toast.makeText(this, "Cannot delete the admin account", Toast.LENGTH_SHORT).show();
            return;
        }

        int rows = getContentResolver().delete(
                GeofenceContract.Users.byUsernameUri(target), null, null);
        Log.i(TAG, "deleteUser: " + target + " -> " + rows + " rows deleted");
        Toast.makeText(this, rows > 0 ? "User deleted" : "No such user", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    /**
     * Resets a user's password to the value in the password input field.
     * Generates a new salt and re-hashes the password.
     * Also clears the user's auth token, forcing them to log in again.
     */
    private void resetPassword() {
        String target = userNameInput.getText().toString().trim().toLowerCase();
        String newPassword = passwordInput.getText().toString();

        // Validate: need a username and a password of at least 6 characters
        if (target.isEmpty() || newPassword == null || newPassword.length() < 6) {
            Toast.makeText(this, "Enter a username and new password (6+ chars)", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate new salt and hash the new password
        String salt = PasswordHasher.generateSalt();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.PASSWORD_HASH, PasswordHasher.hashPassword(newPassword, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.AUTH_TOKEN, "");  // Force re-login

        int rows = getContentResolver().update(
                GeofenceContract.Users.byUsernameUri(target), values, null, null);
        Log.i(TAG, "resetPassword: " + target + " -> " + (rows > 0 ? "success" : "user not found"));
        Toast.makeText(this, rows > 0 ? "Password reset" : "No such user", Toast.LENGTH_SHORT).show();
    }

    /**
     * Adds a geofence pin assigned to the target user.
     * Pin properties (label, lat, lng, radius) come from the input fields.
     */
    private void addPin() {
        String target = userNameInput.getText().toString().trim().toLowerCase();
        String label = pinLabelInput.getText().toString();

        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Pins.USERNAME, target);
        values.put(GeofenceContract.Pins.LABEL, label);
        values.put(GeofenceContract.Pins.LATITUDE, parse(latitudeInput));
        values.put(GeofenceContract.Pins.LONGITUDE, parse(longitudeInput));
        values.put(GeofenceContract.Pins.RADIUS_METERS, parse(radiusInput));
        values.put(GeofenceContract.Pins.ACTIVE, 1);

        boolean ok = getContentResolver().insert(GeofenceContract.Pins.URI, values) != null;
        Log.i(TAG, "addPin: '" + label + "' for " + target + " -> " + (ok ? "success" : "failed"));
        Toast.makeText(this, ok ? "Pin added" : "Could not add pin", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    /**
     * Removes a geofence pin by matching the target username and pin label.
     */
    private void removePin() {
        String target = userNameInput.getText().toString().trim().toLowerCase();
        String label = pinLabelInput.getText().toString();

        int rows = getContentResolver().delete(
                GeofenceContract.Pins.byUsernameUri(target),
                GeofenceContract.Pins.LABEL + " = ?",
                new String[]{label});
        Log.i(TAG, "removePin: '" + label + "' for " + target + " -> " + rows + " rows deleted");
        Toast.makeText(this, rows > 0 ? "Pin removed" : "No such pin", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    /**
     * Refreshes the user list displayed at the bottom of the screen.
     * Shows each user with their role and pin count.
     * If a target username is entered, also shows that user's pin details.
     */
    private void refreshUsers() {
        StringBuilder builder = new StringBuilder("Users:\n");

        // List all users with role and pin count
        Cursor cursor = getContentResolver().query(GeofenceContract.Users.URI, null, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.USERNAME));
                    String role = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Users.ROLE));
                    int pinCount = countPins(name);
                    builder.append("- ")
                            .append(name)
                            .append(" (").append(role).append(")")
                            .append(String.format(Locale.US, " — %d pin%s", pinCount, pinCount == 1 ? "" : "s"))
                            .append("\n");
                }
            } finally {
                cursor.close();
            }
        }

        // If a target username is entered, show their pin details
        String target = userNameInput.getText().toString().trim().toLowerCase();
        if (!target.isEmpty()) {
            builder.append("\nPins for ").append(target).append(":\n");
            Cursor pins = getContentResolver().query(
                    GeofenceContract.Pins.byUsernameUri(target), null, null, null, null);
            if (pins != null) {
                try {
                    if (!pins.moveToFirst()) {
                        builder.append("  (none)\n");
                    } else {
                        do {
                            builder.append("  - ")
                                    .append(pins.getString(pins.getColumnIndexOrThrow(GeofenceContract.Pins.LABEL)))
                                    .append(" (")
                                    .append(pins.getDouble(pins.getColumnIndexOrThrow(GeofenceContract.Pins.LATITUDE)))
                                    .append(", ")
                                    .append(pins.getDouble(pins.getColumnIndexOrThrow(GeofenceContract.Pins.LONGITUDE)))
                                    .append(", r=")
                                    .append(pins.getDouble(pins.getColumnIndexOrThrow(GeofenceContract.Pins.RADIUS_METERS)))
                                    .append("m)\n");
                        } while (pins.moveToNext());
                    }
                } finally {
                    pins.close();
                }
            }
        }

        usersListText.setText(builder.toString());
    }

    /**
     * Counts the number of pins assigned to a specific user.
     *
     * @param username the username to count pins for
     * @return the number of pins, or 0 if none found
     */
    private int countPins(String username) {
        Cursor c = getContentResolver().query(
                GeofenceContract.Pins.byUsernameUri(username), null, null, null, null);
        if (c == null) return 0;
        try {
            return c.getCount();
        } finally {
            c.close();
        }
    }

    /**
     * Parses a double from an EditText, returning 0.0 if parsing fails.
     *
     * @param input the EditText to read from
     * @return the parsed double value, or 0.0 on error
     */
    private double parse(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
