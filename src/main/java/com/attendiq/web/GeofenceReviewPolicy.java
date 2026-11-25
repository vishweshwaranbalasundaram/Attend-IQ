package com.attendiq.web;

import java.util.ArrayList;
import java.util.List;

public final class GeofenceReviewPolicy {
    private GeofenceReviewPolicy() {}

    public static List<FlagReason> softFlags(double distanceMeters, double accuracyMeters,
            double radiusMeters, double maxAccuracyMeters) {
        List<FlagReason> reasons = new ArrayList<>(2);
        if (accuracyMeters > maxAccuracyMeters && accuracyMeters <= maxAccuracyMeters * 2) {
            reasons.add(FlagReason.LOW_ACCURACY);
        }
        if (distanceMeters > radiusMeters && distanceMeters <= radiusMeters * 1.25) {
            reasons.add(FlagReason.NEAR_GEOFENCE_EDGE);
        }
        return List.copyOf(reasons);
    }

    public enum FlagReason {
        LOW_ACCURACY,
        NEAR_GEOFENCE_EDGE
    }
}
