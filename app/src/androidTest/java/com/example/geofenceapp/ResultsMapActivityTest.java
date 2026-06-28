package com.example.geofenceapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.example.geofenceapp.data.GeofenceContract;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class ResultsMapActivityTest {
    private static final double CENTER_LAT = 37.983810;
    private static final double CENTER_LNG = 23.727539;
    private static final double AREA_RADIUS_METERS = 100.0;

    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(
            com.example.geofenceapp.data.GeofenceProvider.class,
            GeofenceContract.AUTHORITY)
            .build();

    @Test
    public void resultsScreenLaunchesAndShowsLatestSessionData() {
        ContentResolver resolver = providerRule.getResolver();
        seedOlderSession(resolver);
        long latestSessionId = seedLatestSessionWithMovement(resolver);

        try (ActivityScenario<ResultsMapActivity> scenario = ActivityScenario.launch(ResultsMapActivity.class)) {
            scenario.onActivity(activity -> {
                Button toggle = activity.findViewById(R.id.toggleServiceButton);
                assertNotNull(toggle);
                assertEquals("Resume service", toggle.getText().toString());
            });
        }

        assertLatestSessionVisibleInResults(resolver, latestSessionId);
    }

    private void seedOlderSession(ContentResolver resolver) {
        long sessionId = createSession(resolver);
        long areaId = createArea(resolver, sessionId);
        createTransition(resolver, sessionId, areaId, CENTER_LAT, CENTER_LNG, GeofenceContract.Transitions.ENTER);
    }

    private long seedLatestSessionWithMovement(ContentResolver resolver) {
        long sessionId = createSession(resolver);
        long areaId = createArea(resolver, sessionId);

        Random random = new Random(123L);
        double[] inside = jitteredPoint(random, true);
        double[] outside = jitteredPoint(random, false);

        createTransition(resolver, sessionId, areaId, inside[0], inside[1], GeofenceContract.Transitions.ENTER);
        createTransition(resolver, sessionId, areaId, outside[0], outside[1], GeofenceContract.Transitions.EXIT);
        createTransition(resolver, sessionId, areaId, inside[0], inside[1], GeofenceContract.Transitions.ENTER);
        return sessionId;
    }

    private long createSession(ContentResolver resolver) {
        android.net.Uri uri = resolver.insert(GeofenceContract.Sessions.URI, new ContentValues());
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private long createArea(ContentResolver resolver, long sessionId) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Areas.SESSION_ID, sessionId);
        values.put(GeofenceContract.Areas.LATITUDE, CENTER_LAT);
        values.put(GeofenceContract.Areas.LONGITUDE, CENTER_LNG);
        values.put(GeofenceContract.Areas.RADIUS_METERS, AREA_RADIUS_METERS);
        android.net.Uri uri = resolver.insert(GeofenceContract.Areas.URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private void createTransition(ContentResolver resolver, long sessionId, long areaId,
                                  double latitude, double longitude, String type) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Transitions.SESSION_ID, sessionId);
        values.put(GeofenceContract.Transitions.AREA_ID, areaId);
        values.put(GeofenceContract.Transitions.LATITUDE, latitude);
        values.put(GeofenceContract.Transitions.LONGITUDE, longitude);
        values.put(GeofenceContract.Transitions.TYPE, type);
        assertNotNull(resolver.insert(GeofenceContract.Transitions.URI, values));
    }

    private void assertLatestSessionVisibleInResults(ContentResolver resolver, long expectedSessionId) {
        Cursor cursor = resolver.query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(expectedSessionId, cursor.getLong(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.SESSION_ID)));
            assertEquals(3, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    private double[] jitteredPoint(Random random, boolean inside) {
        double bearing = random.nextDouble() * Math.PI * 2.0;
        double distanceMeters = inside ? 25.0 + random.nextDouble() * 40.0 : 125.0 + random.nextDouble() * 50.0;
        double deltaLat = (distanceMeters * Math.cos(bearing)) / 111_320.0;
        double deltaLng = (distanceMeters * Math.sin(bearing)) / (111_320.0 * Math.cos(Math.toRadians(CENTER_LAT)));
        return new double[]{CENTER_LAT + deltaLat, CENTER_LNG + deltaLng};
    }
}
