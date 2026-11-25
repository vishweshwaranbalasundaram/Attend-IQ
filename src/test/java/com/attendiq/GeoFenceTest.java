package com.attendiq;

import com.attendiq.web.GeoFence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoFenceTest {
    @Test
    void computesDistancesAcrossTheGeofenceBoundary() {
        double inside = GeoFence.distanceMeters(0, 0, 0, 0.0008);
        double outside = GeoFence.distanceMeters(0, 0, 0, 0.0011);
        assertTrue(inside < 100);
        assertTrue(outside > 100);
        assertEquals(0, GeoFence.distanceMeters(51.5, -0.12, 51.5, -0.12));
    }

    @Test
    void rejectsInvalidGpsCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> GeoFence.distanceMeters(91, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> GeoFence.distanceMeters(0, Double.NaN, 0, 0));
    }
}
