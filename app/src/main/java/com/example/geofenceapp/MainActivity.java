package com.example.geofenceapp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.location.ServiceStarter;
import com.example.geofenceapp.util.AppPrefs;
import com.example.geofenceapp.util.PermissionUtils;

public class MainActivity extends Activity {
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);

        if (!PermissionUtils.hasFineLocation(this)) {
            PermissionUtils.requestForegroundLocation(this);
        }

        updateStatusText();

        findViewById(R.id.openMapButton).setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));
        findViewById(R.id.openResultsButton).setOnClickListener(v ->
                startActivity(new Intent(this, ResultsMapActivity.class)));
        findViewById(R.id.stopTrackingButton).setOnClickListener(this::stopTracking);
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

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusText();
    }

    private void updateStatusText() {
        if (statusText == null) {
            return;
        }
        if (!PermissionUtils.hasFineLocation(this)) {
            statusText.setText(R.string.main_status_needs_location);
        } else if (AppPrefs.isServiceEnabled(this)) {
            statusText.setText(R.string.main_status_tracking);
        } else {
            statusText.setText(R.string.main_status_ready);
        }
    }
}
