package com.example.geofenceapp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeoMathTest {
    @Test
    public void distanceMeters_samePoint_isZero() {
        double distance = GeoMath.distanceMeters(37.983810, 23.727539, 37.983810, 23.727539);

        assertEquals(0.0, distance, 0.001);
    }

    @Test
    public void distanceMeters_betweenAthensAndPiraeus_isAboutEightKilometers() {
        double distance = GeoMath.distanceMeters(37.983810, 23.727539, 37.942030, 23.646190);

        assertTrue("Expected distance near 8.5 km but was " + distance, distance > 8000.0 && distance < 9000.0);
    }

    @Test
    public void distanceMeters_isSymmetric() {
        double first = GeoMath.distanceMeters(40.640063, 22.944419, 35.338735, 25.144213);
        double second = GeoMath.distanceMeters(35.338735, 25.144213, 40.640063, 22.944419);

        assertEquals(first, second, 0.001);
    }

    @Test
    public void distanceMeters_boundaryAroundHundredMeters_staysNearThreshold() {
        double centerLat = 37.983810;
        double centerLng = 23.727539;
        double meters = 100.0;
        double deltaLat = meters / 111_320.0;

        double distance = GeoMath.distanceMeters(centerLat, centerLng, centerLat + deltaLat, centerLng);

        assertTrue("Expected distance near 100m but was " + distance, distance > 95.0 && distance < 105.0);
    }
}
