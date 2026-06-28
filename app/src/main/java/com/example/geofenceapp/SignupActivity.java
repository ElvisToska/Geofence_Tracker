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
 * Signup screen where new users create an account.
 *
 * Uses its own activity_signup.xml layout with a password confirmation field.
 * On successful signup, automatically logs the user in and navigates to MainActivity.
 * Includes a link to switch to LoginActivity.
 */
public class SignupActivity extends Activity {

    private static final String TAG = "SignupActivity";

    private EditText usernameInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        Log.d(TAG, "onCreate");

        // Bind input fields
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        errorText = findViewById(R.id.errorText);

        // Navigation link to switch to the login screen
        findViewById(R.id.switchAuthText).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Signup button
        findViewById(R.id.actionButton).setOnClickListener(v -> doSignup());
    }

    /**
     * Validates the form and attempts to create a new account.
     *
     * Checks that passwords match before calling AuthManager.signUp().
     * On success: auto-logs in and navigates to MainActivity.
     * On failure: shows an appropriate error message.
     */
    private void doSignup() {
        String username = usernameInput.getText().toString();
        String password = passwordInput.getText().toString();
        String confirm = confirmPasswordInput.getText().toString();

        // Check that both password fields match
        if (!password.equals(confirm)) {
            Log.w(TAG, "Signup rejected: passwords do not match");
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(R.string.auth_passwords_mismatch);
            return;
        }

        // Attempt to create the account and auto-login
        boolean ok = AuthManager.signUp(this, username, password);
        if (ok && AuthManager.login(this, username, password)) {
            Log.i(TAG, "Signup and auto-login successful for user: " + username);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Log.w(TAG, "Signup failed for user: " + username);
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(R.string.auth_signup_error);
        }
    }
}
