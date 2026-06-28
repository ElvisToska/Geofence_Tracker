package com.example.geofenceapp;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.geofenceapp.data.GeofenceContract;
import com.example.geofenceapp.location.ServiceStarter;
import com.example.geofenceapp.util.AppPrefs;
import com.example.geofenceapp.util.GeoMath;
import com.example.geofenceapp.util.PermissionUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Map screen where users define circular geofence areas.
 *
 * The user long-presses the map to place 100-meter radius circles.
 * Long-pressing inside an existing circle removes it (toggle behavior).
 * When the user taps "Start", a new tracking session is created with
 * all selected areas, and the GeofenceTrackingService is started.
 */
public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";

    /** Default radius for each geofence area in meters. */
    private static final double AREA_RADIUS_METERS = 100.0;

    /** The Google Map instance. */
    private GoogleMap map;

    /** Used to get the device's last known location for camera positioning. */
    private FusedLocationProviderClient fusedLocationClient;

    /** List of areas the user has placed on the map (not yet saved). */
    private final List<SelectedArea> selectedAreas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        Log.d(TAG, "onCreate");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize the Google Map fragment
        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (fragment != null) {
            fragment.getMapAsync(this);
        }

        // Cancel button: go back without saving
        findViewById(R.id.cancelButton).setOnClickListener(v -> finish());

        // Start button: save all areas and begin tracking
        findViewById(R.id.startButton).setOnClickListener(v -> startSession());
    }

    /**
     * Called when the Google Map is ready to use.
     * Enables the location display and sets up the long-press listener.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        enableLocationDisplay();
        // Long press to add or remove a geofence area
        map.setOnMapLongClickListener(this::toggleAreaAt);
        Log.d(TAG, "Map ready");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.LOCATION_REQUEST && map != null && PermissionUtils.hasFineLocation(this)) {
            enableLocationDisplay();
        }
    }

    /**
     * Enables the "my location" blue dot on the map and moves the camera
     * to the device's last known position.
     */
    private void enableLocationDisplay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtils.requestForegroundLocation(this);
            return;
        }

        map.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this::moveToLocation);
    }

    /**
     * Moves the camera to the given location with a zoom level suitable for geofencing.
     *
     * @param location the location to center on, or null if unavailable
     */
    private void moveToLocation(Location location) {
        if (location != null && map != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
            Log.d(TAG, String.format("Camera moved to %.6f, %.6f", location.getLatitude(), location.getLongitude()));
        }
    }

    /**
     * Toggles a geofence area at the given point.
     * If the point is inside an existing circle, that circle is removed.
     * Otherwise, a new 100m circle is added at the point.
     *
     * @param point the map coordinates where the user long-pressed
     */
    private void toggleAreaAt(LatLng point) {
        // Check if the tap is inside an existing area — if so, remove it
        Iterator<SelectedArea> iterator = selectedAreas.iterator();
        while (iterator.hasNext()) {
            SelectedArea area = iterator.next();
            double distance = GeoMath.distanceMeters(point.latitude, point.longitude, area.center.latitude, area.center.longitude);
            if (distance <= AREA_RADIUS_METERS) {
                area.circle.remove();
                iterator.remove();
                Log.d(TAG, "Area removed at: " + point.latitude + ", " + point.longitude);
                return;
            }
        }

        // Otherwise, add a new circle at the pressed location
        Circle circle = map.addCircle(new CircleOptions()
                .center(point)
                .radius(AREA_RADIUS_METERS)
                .strokeColor(0xFF006D77)
                .fillColor(0x33006D77)
                .strokeWidth(3f));
        selectedAreas.add(new SelectedArea(point, circle));
        Log.d(TAG, "Area added at: " + point.latitude + ", " + point.longitude + " (total: " + selectedAreas.size() + ")");
    }

    /**
     * Creates a new tracking session with all selected areas, saves them
     * to the database, and starts the GeofenceTrackingService.
     */
    private void startSession() {
        // Require at least one area to be defined
        if (selectedAreas.isEmpty()) {
            Toast.makeText(this, "Select at least one area", Toast.LENGTH_SHORT).show();
            return;
        }

        // Require location permission
        if (!PermissionUtils.hasFineLocation(this)) {
            PermissionUtils.requestForegroundLocation(this);
            return;
        }

        // Create a new session in the database
        Uri sessionUri = getContentResolver().insert(GeofenceContract.Sessions.URI, new ContentValues());
        if (sessionUri == null) {
            Toast.makeText(this, "Could not create session", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save each selected area to the database
        long sessionId = ContentUris.parseId(sessionUri);
        for (SelectedArea area : selectedAreas) {
            ContentValues values = new ContentValues();
            values.put(GeofenceContract.Areas.SESSION_ID, sessionId);
            values.put(GeofenceContract.Areas.LATITUDE, area.center.latitude);
            values.put(GeofenceContract.Areas.LONGITUDE, area.center.longitude);
            values.put(GeofenceContract.Areas.RADIUS_METERS, AREA_RADIUS_METERS);
            getContentResolver().insert(GeofenceContract.Areas.URI, values);
        }

        // Persist the session ID and start the tracking service
        AppPrefs.setSessionId(this, sessionId);
        AppPrefs.setServiceEnabled(this, true);
        ServiceStarter.start(this);
        Log.i(TAG, "Session started: id=" + sessionId + " with " + selectedAreas.size() + " areas");
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Holds a reference to a user-selected area on the map,
     * pairing the center coordinates with its visual circle overlay.
     */
    private static class SelectedArea {
        /** The center coordinates of the geofence area. */
        final LatLng center;

        /** The circle overlay drawn on the map. */
        final Circle circle;

        SelectedArea(LatLng center, Circle circle) {
            this.center = center;
            this.circle = circle;
        }
    }
}
