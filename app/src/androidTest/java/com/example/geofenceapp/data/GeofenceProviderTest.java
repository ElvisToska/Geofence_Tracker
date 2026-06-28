package com.example.geofenceapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class GeofenceProviderTest {
    private static final double CENTER_LAT = 37.983810;
    private static final double CENTER_LNG = 23.727539;
    private static final double AREA_RADIUS_METERS = 100.0;

    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(
            GeofenceProvider.class,
            GeofenceContract.AUTHORITY)
            .build();

    @Test
    public void insertSessionAndArea_currentAreasReturnsSelectedArea() {
        ContentResolver resolver = providerRule.getResolver();
        long sessionId = createSession(resolver);

        ContentValues area = new ContentValues();
        area.put(GeofenceContract.Areas.SESSION_ID, sessionId);
        area.put(GeofenceContract.Areas.LATITUDE, 37.983810);
        area.put(GeofenceContract.Areas.LONGITUDE, 23.727539);
        area.put(GeofenceContract.Areas.RADIUS_METERS, 100.0);
        Uri areaUri = resolver.insert(GeofenceContract.Areas.URI, area);

        assertNotNull(areaUri);

        Cursor cursor = resolver.query(GeofenceContract.Areas.CURRENT_URI, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(sessionId, cursor.getLong(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.SESSION_ID)));
            assertEquals(37.983810, cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LATITUDE)), 0.000001);
            assertEquals(23.727539, cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LONGITUDE)), 0.000001);
            assertEquals(100.0, cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.RADIUS_METERS)), 0.001);
        } finally {
            cursor.close();
        }
    }

    @Test
    public void movementSequence_lastQueriesExposeStoredAreasAndTransitions() {
        ContentResolver resolver = providerRule.getResolver();
        long sessionId = createSession(resolver);
        long areaId = createArea(resolver, sessionId);

        Random random = new Random(42L);
        double[][] points = new double[][]{
                jitteredPoint(random, true),
                jitteredPoint(random, false),
                jitteredPoint(random, true),
                jitteredPoint(random, false),
                jitteredPoint(random, true)
        };

        String[] expectedTypes = new String[]{
                GeofenceContract.Transitions.ENTER,
                GeofenceContract.Transitions.EXIT,
                GeofenceContract.Transitions.ENTER,
                GeofenceContract.Transitions.EXIT,
                GeofenceContract.Transitions.ENTER
        };

        for (int i = 0; i < points.length; i++) {
            createTransition(
                    resolver,
                    sessionId,
                    areaId,
                    points[i][0],
                    points[i][1],
                    expectedTypes[i]);
        }

        assertSingleAreaVisibleInResults(resolver, sessionId);
        assertTransitionSequenceVisibleInResults(resolver, expectedTypes, points);
    }

    @Test
    public void repeatedMovesOnSameSide_doNotCreateExtraTransitions() {
        ContentResolver resolver = providerRule.getResolver();
        long sessionId = createSession(resolver);
        long areaId = createArea(resolver, sessionId);

        double[][] insidePoints = new double[][]{
                jitteredPoint(new Random(7L), true),
                jitteredPoint(new Random(8L), true),
                jitteredPoint(new Random(9L), true)
        };

        createTransition(resolver, sessionId, areaId, insidePoints[0][0], insidePoints[0][1], GeofenceContract.Transitions.ENTER);
        createTransition(resolver, sessionId, areaId, insidePoints[1][0], insidePoints[1][1], GeofenceContract.Transitions.ENTER);
        createTransition(resolver, sessionId, areaId, insidePoints[2][0], insidePoints[2][1], GeofenceContract.Transitions.ENTER);

        Cursor cursor = resolver.query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertEquals(3, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(GeofenceContract.Transitions.ENTER,
                    cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE)));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void latestSession_isWhatResultsQueryReturns() {
        ContentResolver resolver = providerRule.getResolver();

        long firstSessionId = createSession(resolver);
        long firstAreaId = createArea(resolver, firstSessionId);
        createTransition(resolver, firstSessionId, firstAreaId, CENTER_LAT, CENTER_LNG, GeofenceContract.Transitions.ENTER);

        long secondSessionId = createSession(resolver);
        long secondAreaId = createArea(resolver, secondSessionId);
        double[] outsidePoint = jitteredPoint(new Random(99L), false);
        createTransition(resolver, secondSessionId, secondAreaId, outsidePoint[0], outsidePoint[1], GeofenceContract.Transitions.EXIT);

        Cursor areasCursor = resolver.query(GeofenceContract.Areas.LAST_URI, null, null, null, null);
        assertNotNull(areasCursor);
        try {
            assertEquals(1, areasCursor.getCount());
            assertTrue(areasCursor.moveToFirst());
            assertEquals(secondSessionId, areasCursor.getLong(areasCursor.getColumnIndexOrThrow(GeofenceContract.Areas.SESSION_ID)));
        } finally {
            areasCursor.close();
        }

        Cursor transitionsCursor = resolver.query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        assertNotNull(transitionsCursor);
        try {
            assertEquals(1, transitionsCursor.getCount());
            assertTrue(transitionsCursor.moveToFirst());
            assertEquals(secondSessionId, transitionsCursor.getLong(transitionsCursor.getColumnIndexOrThrow(GeofenceContract.Transitions.SESSION_ID)));
            assertEquals(GeofenceContract.Transitions.EXIT,
                    transitionsCursor.getString(transitionsCursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE)));
        } finally {
            transitionsCursor.close();
        }
    }

    @Test
    public void insertTransitions_lastTransitionsReturnsEnterAndExitPoints() {
        ContentResolver resolver = providerRule.getResolver();
        long sessionId = createSession(resolver);
        long areaId = createArea(resolver, sessionId);

        createTransition(resolver, sessionId, areaId, 37.983810, 23.727539, GeofenceContract.Transitions.ENTER);
        createTransition(resolver, sessionId, areaId, 37.984200, 23.728100, GeofenceContract.Transitions.EXIT);

        Cursor cursor = resolver.query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(GeofenceContract.Transitions.ENTER,
                    cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE)));
            assertTrue(cursor.moveToNext());
            assertEquals(GeofenceContract.Transitions.EXIT,
                    cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE)));
        } finally {
            cursor.close();
        }
    }

    private long createSession(ContentResolver resolver) {
        Uri uri = resolver.insert(GeofenceContract.Sessions.URI, new ContentValues());
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private long createArea(ContentResolver resolver, long sessionId) {
        ContentValues values = new ContentValues();
        values.put(GeofenceContract.Areas.SESSION_ID, sessionId);
        values.put(GeofenceContract.Areas.LATITUDE, CENTER_LAT);
        values.put(GeofenceContract.Areas.LONGITUDE, CENTER_LNG);
        values.put(GeofenceContract.Areas.RADIUS_METERS, AREA_RADIUS_METERS);
        Uri uri = resolver.insert(GeofenceContract.Areas.URI, values);
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

    private void assertSingleAreaVisibleInResults(ContentResolver resolver, long sessionId) {
        Cursor cursor = resolver.query(GeofenceContract.Areas.LAST_URI, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(sessionId, cursor.getLong(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.SESSION_ID)));
            assertEquals(CENTER_LAT, cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LATITUDE)), 0.000001);
            assertEquals(CENTER_LNG, cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.LONGITUDE)), 0.000001);
            assertEquals(AREA_RADIUS_METERS, cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Areas.RADIUS_METERS)), 0.001);
        } finally {
            cursor.close();
        }
    }

    private void assertTransitionSequenceVisibleInResults(ContentResolver resolver, String[] expectedTypes, double[][] points) {
        Cursor cursor = resolver.query(GeofenceContract.Transitions.LAST_URI, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertEquals(expectedTypes.length, cursor.getCount());
            int index = 0;
            while (cursor.moveToNext()) {
                assertEquals(expectedTypes[index], cursor.getString(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.TYPE)));
                assertEquals(points[index][0], cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.LATITUDE)), 0.000001);
                assertEquals(points[index][1], cursor.getDouble(cursor.getColumnIndexOrThrow(GeofenceContract.Transitions.LONGITUDE)), 0.000001);
                index++;
            }
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
