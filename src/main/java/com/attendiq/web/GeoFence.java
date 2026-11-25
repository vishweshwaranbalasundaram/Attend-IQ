package com.attendiq.web;

public final class GeoFence {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoFence() {}

    public static double distanceMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        validateCoordinates(latitude1, longitude1);
        validateCoordinates(latitude2, longitude2);
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double bounded = Math.min(1.0, Math.max(0.0, haversine));
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(bounded));
    }

    public static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("GPS coordinates are outside valid latitude/longitude ranges.");
        }
    }
}
