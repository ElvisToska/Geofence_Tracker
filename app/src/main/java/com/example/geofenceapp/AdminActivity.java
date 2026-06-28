package com.example.geofenceapp;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.util.AuthManager;
import com.example.geofenceapp.util.PasswordHasher;

public class AdminActivity extends Activity {
    private EditText userNameInput;
    private EditText passwordInput;
    private EditText pinLabelInput;
    private EditText latitudeInput;
    private EditText longitudeInput;
    private EditText radiusInput;
    private TextView usersListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        if (!AuthManager.isAdmin(this)) {
            Toast.makeText(this, "Admin access only", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userNameInput = findViewById(R.id.userNameInput);
        passwordInput = findViewById(R.id.passwordInput);
        pinLabelInput = findViewById(R.id.pinLabelInput);
        latitudeInput = findViewById(R.id.latitudeInput);
        longitudeInput = findViewById(R.id.longitudeInput);
        radiusInput = findViewById(R.id.radiusInput);
        usersListText = findViewById(R.id.usersListText);

        findViewById(R.id.addUserButton).setOnClickListener(v -> addUser());
        findViewById(R.id.deleteUserButton).setOnClickListener(v -> deleteUser());
        findViewById(R.id.resetPasswordButton).setOnClickListener(v -> resetPassword());
        findViewById(R.id.addPinButton).setOnClickListener(v -> addPin());
        findViewById(R.id.removePinButton).setOnClickListener(v -> removePin());
        refreshUsers();
    }

    private void addUser() {
        boolean ok = AuthManager.signUp(this, userNameInput.getText().toString(), passwordInput.getText().toString());
        Toast.makeText(this, ok ? "User added" : "Could not add user", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    private void deleteUser() {
        String target = userNameInput.getText().toString().trim().toLowerCase();
        if (target.equals(AuthManager.ADMIN_USERNAME)) {
            Toast.makeText(this, "Cannot delete the admin account", Toast.LENGTH_SHORT).show();
            return;
        }
        int rows = getContentResolver().delete(
                GeofenceContract.Users.byUsernameUri(target),
                null,
                null);
        Toast.makeText(this, rows > 0 ? "User deleted" : "No such user", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    private void resetPassword() {
        String target = userNameInput.getText().toString().trim().toLowerCase();
        String newPassword = passwordInput.getText().toString();
        if (target.isEmpty() || newPassword == null || newPassword.length() < 6) {
            Toast.makeText(this, "Enter a username and new password (6+ chars)", Toast.LENGTH_SHORT).show();
            return;
        }
        String salt = PasswordHasher.generateSalt();
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Users.PASSWORD_HASH, PasswordHasher.hashPassword(newPassword, salt));
        values.put(GeofenceContract.Users.PASSWORD_SALT, salt);
        values.put(GeofenceContract.Users.AUTH_TOKEN, "");
        int rows = getContentResolver().update(
                GeofenceContract.Users.byUsernameUri(target), values, null, null);
        Toast.makeText(this, rows > 0 ? "Password reset" : "No such user", Toast.LENGTH_SHORT).show();
    }

    private void addPin() {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Pins.USERNAME, userNameInput.getText().toString().trim().toLowerCase());
        values.put(GeofenceContract.Pins.LABEL, pinLabelInput.getText().toString());
        values.put(GeofenceContract.Pins.LATITUDE, parse(latitudeInput));
        values.put(GeofenceContract.Pins.LONGITUDE, parse(longitudeInput));
        values.put(GeofenceContract.Pins.RADIUS_METERS, parse(radiusInput));
        values.put(GeofenceContract.Pins.ACTIVE, 1);
        boolean ok = getContentResolver().insert(GeofenceContract.Pins.URI, values) != null;
        Toast.makeText(this, ok ? "Pin added" : "Could not add pin", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    private void removePin() {
        int rows = getContentResolver().delete(
                GeofenceContract.Pins.byUsernameUri(userNameInput.getText().toString().trim().toLowerCase()),
                GeofenceContract.Pins.LABEL + " = ?",
                new String[]{pinLabelInput.getText().toString()});
        Toast.makeText(this, rows > 0 ? "Pin removed" : "No such pin", Toast.LENGTH_SHORT).show();
        refreshUsers();
    }

    private void refreshUsers() {
        StringBuilder builder = new StringBuilder("Users:\n");
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

    private double parse(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
