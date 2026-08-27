package com.frauddetect.fraud.feature;

/** Great-circle distance helper (Haversine). Used for the impossible-travel detection. */
public final class GeoUtil {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtil() {
    }

    /**
     * Distance in kilometres between two lat/long points. Returns 0 when either point is missing
     * (we cannot infer travel, so we must not fabricate a distance-based signal).
     */
    public static double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0.0;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
