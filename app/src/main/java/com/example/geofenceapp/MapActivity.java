package com.example.geofenceapp;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
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

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final double AREA_RADIUS_METERS = 100.0;

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private final List<SelectedArea> selectedAreas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (fragment != null) {
            fragment.getMapAsync(this);
        }

        findViewById(R.id.cancelButton).setOnClickListener(v -> finish());
        findViewById(R.id.startButton).setOnClickListener(v -> startSession());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        enableLocationDisplay();
        map.setOnMapLongClickListener(this::toggleAreaAt);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtils.LOCATION_REQUEST && map != null && PermissionUtils.hasFineLocation(this)) {
            enableLocationDisplay();
        }
    }

    private void enableLocationDisplay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtils.requestForegroundLocation(this);
            return;
        }

        map.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this::moveToLocation);
    }

    private void moveToLocation(Location location) {
        if (location != null && map != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
        }
    }

    private void toggleAreaAt(LatLng point) {
        Iterator<SelectedArea> iterator = selectedAreas.iterator();
        while (iterator.hasNext()) {
            SelectedArea area = iterator.next();
            double distance = GeoMath.distanceMeters(point.latitude, point.longitude, area.center.latitude, area.center.longitude);
            if (distance <= AREA_RADIUS_METERS) {
                area.circle.remove();
                iterator.remove();
                return;
            }
        }

        Circle circle = map.addCircle(new CircleOptions()
                .center(point)
                .radius(AREA_RADIUS_METERS)
                .strokeColor(0xFF006D77)
                .fillColor(0x33006D77)
                .strokeWidth(3f));
        selectedAreas.add(new SelectedArea(point, circle));
    }

    private void startSession() {
        if (selectedAreas.isEmpty()) {
            Toast.makeText(this, "Select at least one area", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PermissionUtils.hasFineLocation(this)) {
            PermissionUtils.requestForegroundLocation(this);
            return;
        }

        Uri sessionUri = getContentResolver().insert(GeofenceContract.Sessions.URI, new ContentValues());
        if (sessionUri == null) {
            Toast.makeText(this, "Could not create session", Toast.LENGTH_SHORT).show();
            return;
        }

        long sessionId = ContentUris.parseId(sessionUri);
        for (SelectedArea area : selectedAreas) {
            ContentValues values = new ContentValues();
            values.put(GeofenceContract.Areas.SESSION_ID, sessionId);
            values.put(GeofenceContract.Areas.LATITUDE, area.center.latitude);
            values.put(GeofenceContract.Areas.LONGITUDE, area.center.longitude);
            values.put(GeofenceContract.Areas.RADIUS_METERS, AREA_RADIUS_METERS);
            getContentResolver().insert(GeofenceContract.Areas.URI, values);
        }

        AppPrefs.setSessionId(this, sessionId);
        AppPrefs.setServiceEnabled(this, true);
        ServiceStarter.start(this);
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static class SelectedArea {
        final LatLng center;
        final Circle circle;

        SelectedArea(LatLng center, Circle circle) {
            this.center = center;
            this.circle = circle;
        }
    }
}
