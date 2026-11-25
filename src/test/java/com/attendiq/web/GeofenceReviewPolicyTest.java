package com.attendiq.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeofenceReviewPolicyTest {
    @Test
    void flagsOnlyBorderlineAccuracyAndDistanceForReview() {
        assertEquals(List.of(
                GeofenceReviewPolicy.FlagReason.LOW_ACCURACY,
                GeofenceReviewPolicy.FlagReason.NEAR_GEOFENCE_EDGE),
                GeofenceReviewPolicy.softFlags(180, 75, 150, 50));
    }

    @Test
    void doesNotFlagWithinLimitsOrBeyondTheSoftReviewWindow() {
        assertEquals(List.of(), GeofenceReviewPolicy.softFlags(150, 50, 150, 50));
        assertEquals(List.of(), GeofenceReviewPolicy.softFlags(190, 101, 150, 50));
        assertEquals(List.of(GeofenceReviewPolicy.FlagReason.LOW_ACCURACY),
                GeofenceReviewPolicy.softFlags(200, 100, 150, 50));
        assertEquals(List.of(), GeofenceReviewPolicy.softFlags(200, 100.1, 150, 50));
    }
}
