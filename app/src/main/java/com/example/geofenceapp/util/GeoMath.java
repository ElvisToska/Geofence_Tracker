package com.example.geofenceapp.util;

/**
 * Pure math utility for calculating distances between GPS coordinates.
 *
 * Uses the Haversine formula to compute the great-circle distance between
 * two points on Earth, which is accurate enough for geofence radius checks
 * (typically 50–500 meters).
 */
public final class GeoMath {

    /** Mean radius of the Earth in meters (WGS-84 approximation). */
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /** Private constructor — this class is used only through its static methods. */
    private GeoMath() {
    }

    /**
     * Calculates the distance in meters between two GPS coordinates
     * using the Haversine formula.
     *
     * @param lat1 latitude of the first point in degrees
     * @param lon1 longitude of the first point in degrees
     * @param lat2 latitude of the second point in degrees
     * @param lon2 longitude of the second point in degrees
     * @return the distance between the two points in meters
     */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        // Convert degrees to radians
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        // Haversine formula: a = sin²(Δlat/2) + cos(lat1)·cos(lat2)·sin²(Δlon/2)
        double a = Math.sin(deltaPhi / 2.0) * Math.sin(deltaPhi / 2.0)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2.0) * Math.sin(deltaLambda / 2.0);

        // Central angle: c = 2·atan2(√a, √(1−a))
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        // Distance = Earth radius × central angle
        return EARTH_RADIUS_METERS * c;
    }
}
