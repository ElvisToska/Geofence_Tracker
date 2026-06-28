package com.example.geofenceapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.geofenceapp.util.AuthManager;

/**
 * Login screen where users authenticate with their username and password.
 *
 * Uses the shared activity_auth.xml layout (same layout as the old signup,
 * but without the confirm password field). On successful login, navigates
 * to MainActivity. Includes a link to switch to SignupActivity.
 */
public class LoginActivity extends Activity {

    private static final String TAG = "LoginActivity";

    private EditText usernameInput;
    private EditText passwordInput;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        Log.d(TAG, "onCreate");

        // Set the title and button text for login mode
        ((TextView) findViewById(R.id.authTitleText)).setText(R.string.auth_title_login);
        findViewById(R.id.actionButton).setBackgroundResource(R.drawable.button_primary);

        // Bind input fields
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);

        // Navigation link to switch to the signup screen
        TextView switchText = findViewById(R.id.switchAuthText);
        switchText.setText(R.string.auth_switch_to_signup);
        switchText.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });

        // Login button
        findViewById(R.id.actionButton).setOnClickListener(v -> doLogin());
    }

    /**
     * Attempts to log in with the entered credentials.
     * On success: navigates to MainActivity.
     * On failure: shows an error message.
     */
    private void doLogin() {
        String username = usernameInput.getText().toString();
        boolean ok = AuthManager.login(this, username, passwordInput.getText().toString());
        if (ok) {
            Log.i(TAG, "Login successful for user: " + username);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Log.w(TAG, "Login failed for user: " + username);
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(R.string.auth_error);
        }
    }
}
