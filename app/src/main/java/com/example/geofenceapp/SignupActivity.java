package com.example.geofenceapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.geofenceapp.util.AuthManager;

public class SignupActivity extends Activity {
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        errorText = findViewById(R.id.errorText);

        findViewById(R.id.switchAuthText).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.actionButton).setOnClickListener(v -> doSignup());
    }

    private void doSignup() {
        String password = passwordInput.getText().toString();
        String confirm = confirmPasswordInput.getText().toString();
        if (!password.equals(confirm)) {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(R.string.auth_passwords_mismatch);
            return;
        }
        boolean ok = AuthManager.signUp(this,
                usernameInput.getText().toString(), password);
        if (ok && AuthManager.login(this, usernameInput.getText().toString(), password)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(R.string.auth_signup_error);
        }
    }
}
