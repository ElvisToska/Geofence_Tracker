package com.example.geofenceapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.location.ServiceStarter;
import com.example.geofenceapp.util.AppPrefs;
import com.example.geofenceapp.util.PermissionUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Results screen that displays the most recent tracking session on a map.
 *
 * Shows:
 * - Geofence circles from the last session
 * - Enter markers (green) and exit markers (orange) from the last session
 * - The device's current location (blue marker)
 *
 * Also provides a button to pause or resume the tracking service.
 * If no session data exists, shows an "empty results" message.
 */
public class ResultsMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "ResultsMapActivity";

    /** The Google Map instance. */
    private GoogleMap map;

    /** Button to toggle the tracking service on/off. */
    private Button toggleServiceButton;

    /** Text shown when there are no results to display. */
    private TextView emptyResultsText;

    /** Used to get the device's last known location. */
    private FusedLocationProviderClient fusedLocationClient;

    /** Accumulates all displayed points for auto-zooming the camera. */
    private LatLngBounds.Builder boundsBuilder;

    /** Whether any points have been added to the bounds builder. */
    private boolean hasBounds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results_map);
        Log.d(TAG, "onCreate");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        toggleServiceButton = findViewById(R.id.toggleServiceButton);
        emptyResultsText = findViewById(R.id.emptyResultsText);

        // Initialize the Google Map fragment
        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.resultsMapFragment);
        if (fragment != null) {
            fragment.getMapAsync(this);
        }

        // Toggle tracking service on/off
        toggleServiceButton.setOnClickListener(v -> toggleService());

        // Back button: return to the main screen
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        updateToggleText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToggleText();
    }

    /**
     * Called when the Google Map is ready.
     * Loads the last session's areas, transitions, and current location.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        boundsBuilder = LatLngBounds.builder();
        showLastAreas();
        showLastTransitions();
        showCurrentLocation();
        updateEmptyState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.LOCATION_REQUEST && map != null && PermissionUtils.hasFineLocation(this)) {
            showCurrentLocation();
        }
    }

    /**
     * Draws the geofence circles from the most recent session on the map.
     */
    private void showLastAreas() {
        Cursor cursor = getContentResolver().query(GeofenceContract.Areas.LAST_URI, null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            int count = 0;
            while (cursor.moveToNext()) {
                LatLng center = new LatLng(
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LONGITUDE)));
                double radius = cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.RADIUS_METERS));

                // Draw the circle with a semi-transparent fill
                map.addCircle(new CircleOptions()
                        .center(center)
                        .radius(radius)
                        .strokeColor(0xFF006D77)
                        .fillColor(0x22006D77)
                        .strokeWidth(3f));
                include(center);
                count++;
            }
            Log.d(TAG, "Displayed " + count + " areas from last session");
        } finally {
            cursor.close();
        }
    }

    /**
     * Draws enter/exit markers from the most recent session on the map.
     * ENTER events get green markers, EXIT events get orange markers.
     */
    private void showLastTransitions() {
        Cursor cursor = getContentResolver().query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            int count = 0;
            while (cursor.moveToNext()) {
                String type = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE));
                LatLng point = new LatLng(
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.LONGITUDE)));

                // Green for ENTER, orange for EXIT
                float color = GeofenceContract.Transitions.ENTER.equals(type)
                        ? BitmapDescriptorFactory.HUE_GREEN
                        : BitmapDescriptorFactory.HUE_ORANGE;

                map.addMarker(new MarkerOptions()
                        .position(point)
                        .title(type)
                        .icon(BitmapDescriptorFactory.defaultMarker(color)));
                include(point);
                count++;
            }
            Log.d(TAG, "Displayed " + count + " transitions from last session");
        } finally {
            cursor.close();
        }
    }

    /**
     * Shows the device's current location as a blue marker on the map.
     * Also enables the "my location" blue dot overlay.
     */
    private void showCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtils.requestForegroundLocation(this);
            zoomToResults();
            return;
        }

        map.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this::addCurrentLocation);
    }

    /**
     * Adds a blue marker at the device's current location.
     *
     * @param location the current GPS location, or null if unavailable
     */
    private void addCurrentLocation(Location location) {
        if (location != null) {
            LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
            map.addMarker(new MarkerOptions()
                    .position(current)
                    .title("Current location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            include(current);
        }
        zoomToResults();
        updateEmptyState();
    }

    /**
     * Toggles the tracking service between running and paused.
     */
    private void toggleService() {
        boolean enabled = AppPrefs.isServiceEnabled(this);
        AppPrefs.setServiceEnabled(this, !enabled);
        if (enabled) {
            ServiceStarter.stop(this);
            Log.i(TAG, "Tracking paused by user");
        } else {
            ServiceStarter.start(this);
            Log.i(TAG, "Tracking resumed by user");
        }
        updateToggleText();
    }

    /** Updates the toggle button text to reflect the current service state. */
    private void updateToggleText() {
        if (toggleServiceButton != null) {
            toggleServiceButton.setText(AppPrefs.isServiceEnabled(this) ? "Pause service" : "Resume service");
        }
    }

    /** Adds a point to the bounds builder for auto-zooming. */
    private void include(LatLng point) {
        boundsBuilder.include(point);
        hasBounds = true;
        updateEmptyState();
    }

    /** Zooms the camera to fit all displayed markers and circles. */
    private void zoomToResults() {
        if (hasBounds) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));
        }
    }

    /** Shows or hides the "no results" message based on whether data was loaded. */
    private void updateEmptyState() {
        if (emptyResultsText != null) {
            emptyResultsText.setVisibility(hasBounds ? View.GONE : View.VISIBLE);
        }
    }
}
