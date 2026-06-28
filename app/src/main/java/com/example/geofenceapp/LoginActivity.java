package com.example.geofenceapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.geofenceapp.util.AuthManager;

public class LoginActivity extends Activity {
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        ((TextView) findViewById(R.id.authTitleText)).setText(R.string.auth_title_login);
        findViewById(R.id.actionButton).setBackgroundResource(R.drawable.button_primary);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);

        TextView switchText = findViewById(R.id.switchAuthText);
        switchText.setText(R.string.auth_switch_to_signup);
        switchText.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });

        findViewById(R.id.actionButton).setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        boolean ok = AuthManager.login(this,
                usernameInput.getText().toString(),
                passwordInput.getText().toString());
        if (ok) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(R.string.auth_error);
        }
    }
}
