package com.example.geofenceapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
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

public class ResultsMapActivity extends FragmentActivity implements OnMapReadyCallback {
    private GoogleMap map;
    private Button toggleServiceButton;
    private TextView emptyResultsText;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLngBounds.Builder boundsBuilder;
    private boolean hasBounds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results_map);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        toggleServiceButton = findViewById(R.id.toggleServiceButton);
        emptyResultsText = findViewById(R.id.emptyResultsText);

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.resultsMapFragment);
        if (fragment != null) {
            fragment.getMapAsync(this);
        }

        toggleServiceButton.setOnClickListener(v -> toggleService());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        updateToggleText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToggleText();
    }

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

    private void showLastAreas() {
        Cursor cursor = getContentResolver().query(GeofenceContract.Areas.LAST_URI, null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {
                LatLng center = new LatLng(
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LONGITUDE)));
                double radius = cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.RADIUS_METERS));
                map.addCircle(new CircleOptions()
                        .center(center)
                        .radius(radius)
                        .strokeColor(0xFF006D77)
                        .fillColor(0x22006D77)
                        .strokeWidth(3f));
                include(center);
            }
        } finally {
            cursor.close();
        }
    }

    private void showLastTransitions() {
        Cursor cursor = getContentResolver().query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {
                String type = cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE));
                LatLng point = new LatLng(
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.LONGITUDE)));
                float color = GeofenceContract.Transitions.ENTER.equals(type)
                        ? BitmapDescriptorFactory.HUE_GREEN
                        : BitmapDescriptorFactory.HUE_ORANGE;
                map.addMarker(new MarkerOptions()
                        .position(point)
                        .title(type)
                        .icon(BitmapDescriptorFactory.defaultMarker(color)));
                include(point);
            }
        } finally {
            cursor.close();
        }
    }

    private void showCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtils.requestForegroundLocation(this);
            zoomToResults();
            return;
        }

        map.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this::addCurrentLocation);
    }

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

    private void toggleService() {
        boolean enabled = AppPrefs.isServiceEnabled(this);
        AppPrefs.setServiceEnabled(this, !enabled);
        if (enabled) {
            ServiceStarter.stop(this);
        } else {
            ServiceStarter.start(this);
        }
        updateToggleText();
    }

    private void updateToggleText() {
        if (toggleServiceButton != null) {
            toggleServiceButton.setText(AppPrefs.isServiceEnabled(this) ? "Pause service" : "Resume service");
        }
    }

    private void include(LatLng point) {
        boundsBuilder.include(point);
        hasBounds = true;
        updateEmptyState();
    }

    private void zoomToResults() {
        if (hasBounds) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));
        }
    }

    private void updateEmptyState() {
        if (emptyResultsText != null) {
            emptyResultsText.setVisibility(hasBounds ? View.GONE : View.VISIBLE);
        }
    }
}
