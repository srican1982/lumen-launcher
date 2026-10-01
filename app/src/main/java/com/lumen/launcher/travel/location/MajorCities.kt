package com.lumen.launcher.travel.location

/**
 * Major travel destinations used to collapse suburbs into real trip cities
 * (Cerritos/Norwalk → Los Angeles; wards → Tokyo / Osaka / Kyoto).
 */
internal data class MajorCity(
    val name: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    /** Photos within this radius (km) are labeled with this city. */
    val radiusKm: Double = 40.0
)

internal object MajorCities {
    val all: List<MajorCity> = listOf(
        // United States
        MajorCity("Los Angeles", "US", 34.0522, -118.2437, 55.0),
        MajorCity("San Francisco", "US", 37.7749, -122.4194, 45.0),
        MajorCity("San Diego", "US", 32.7157, -117.1611, 40.0),
        MajorCity("New York", "US", 40.7128, -74.0060, 45.0),
        MajorCity("Chicago", "US", 41.8781, -87.6298, 45.0),
        MajorCity("Miami", "US", 25.7617, -80.1918, 40.0),
        MajorCity("Las Vegas", "US", 36.1699, -115.1398, 35.0),
        MajorCity("Seattle", "US", 47.6062, -122.3321, 40.0),
        MajorCity("Boston", "US", 42.3601, -71.0589, 35.0),
        MajorCity("Washington", "US", 38.9072, -77.0369, 35.0),
        MajorCity("Phoenix", "US", 33.4484, -112.0740, 45.0),
        MajorCity("Denver", "US", 39.7392, -104.9903, 40.0),
        MajorCity("Dallas", "US", 32.7767, -96.7970, 45.0),
        MajorCity("Houston", "US", 29.7604, -95.3698, 45.0),
        MajorCity("Austin", "US", 30.2672, -97.7431, 35.0),
        MajorCity("Portland", "US", 45.5152, -122.6784, 35.0),
        MajorCity("Honolulu", "US", 21.3069, -157.8583, 30.0),
        MajorCity("Philadelphia", "US", 39.9526, -75.1652, 35.0),
        MajorCity("Atlanta", "US", 33.7490, -84.3880, 40.0),
        MajorCity("New Orleans", "US", 29.9511, -90.0715, 30.0),
        MajorCity("Nashville", "US", 36.1627, -86.7816, 30.0),
        MajorCity("Orlando", "US", 28.5383, -81.3792, 35.0),
        // Japan — prefecture / metro hubs, not wards
        MajorCity("Tokyo", "JP", 35.6762, 139.6503, 45.0),
        MajorCity("Yokohama", "JP", 35.4437, 139.6380, 20.0),
        MajorCity("Osaka", "JP", 34.6937, 135.5023, 30.0),
        MajorCity("Kyoto", "JP", 35.0116, 135.7681, 25.0),
        MajorCity("Nagoya", "JP", 35.1815, 136.9066, 30.0),
        MajorCity("Sapporo", "JP", 43.0618, 141.3545, 30.0),
        MajorCity("Fukuoka", "JP", 33.5904, 130.4017, 25.0),
        MajorCity("Kobe", "JP", 34.6901, 135.1955, 20.0),
        MajorCity("Hiroshima", "JP", 34.3853, 132.4553, 25.0),
        MajorCity("Nara", "JP", 34.6851, 135.8048, 18.0),
        MajorCity("Okinawa", "JP", 26.3344, 127.8056, 40.0),
        // Korea
        MajorCity("Seoul", "KR", 37.5665, 126.9780, 40.0),
        MajorCity("Busan", "KR", 35.1796, 129.0756, 30.0),
        // Europe
        MajorCity("London", "GB", 51.5074, -0.1278, 40.0),
        MajorCity("Paris", "FR", 48.8566, 2.3522, 35.0),
        MajorCity("Rome", "IT", 41.9028, 12.4964, 30.0),
        MajorCity("Florence", "IT", 43.7696, 11.2558, 20.0),
        MajorCity("Venice", "IT", 45.4408, 12.3155, 20.0),
        MajorCity("Milan", "IT", 45.4642, 9.1900, 30.0),
        MajorCity("Barcelona", "ES", 41.3874, 2.1686, 30.0),
        MajorCity("Madrid", "ES", 40.4168, -3.7038, 35.0),
        MajorCity("Berlin", "DE", 52.5200, 13.4050, 35.0),
        MajorCity("Munich", "DE", 48.1351, 11.5820, 30.0),
        MajorCity("Amsterdam", "NL", 52.3676, 4.9041, 25.0),
        MajorCity("Prague", "CZ", 50.0755, 14.4378, 25.0),
        MajorCity("Vienna", "AT", 48.2082, 16.3738, 25.0),
        MajorCity("Lisbon", "PT", 38.7223, -9.1393, 25.0),
        MajorCity("Athens", "GR", 37.9838, 23.7275, 30.0),
        MajorCity("Istanbul", "TR", 41.0082, 28.9784, 40.0),
        MajorCity("Dubai", "AE", 25.2048, 55.2708, 40.0),
        // Asia-Pacific
        MajorCity("Bangkok", "TH", 13.7563, 100.5018, 40.0),
        MajorCity("Singapore", "SG", 1.3521, 103.8198, 25.0),
        MajorCity("Hong Kong", "HK", 22.3193, 114.1694, 30.0),
        MajorCity("Taipei", "TW", 25.0330, 121.5654, 30.0),
        MajorCity("Beijing", "CN", 39.9042, 116.4074, 45.0),
        MajorCity("Shanghai", "CN", 31.2304, 121.4737, 45.0),
        MajorCity("Sydney", "AU", -33.8688, 151.2093, 40.0),
        MajorCity("Melbourne", "AU", -37.8136, 144.9631, 40.0),
        MajorCity("Auckland", "NZ", -36.8509, 174.7645, 30.0),
        MajorCity("Toronto", "CA", 43.6532, -79.3832, 40.0),
        MajorCity("Vancouver", "CA", 49.2827, -123.1207, 35.0),
        MajorCity("Mexico City", "MX", 19.4326, -99.1332, 40.0),
        MajorCity("Cairo", "EG", 30.0444, 31.2357, 35.0),
        MajorCity("Cape Town", "ZA", -33.9249, 18.4241, 35.0)
    )

    fun nearest(latitude: Double, longitude: Double, countryCode: String?): MajorCity? {
        val code = countryCode?.trim()?.uppercase()
        val candidates = if (code.isNullOrBlank()) all else all.filter { it.countryCode == code }
            .ifEmpty { all }
        var best: MajorCity? = null
        var bestKm = Double.MAX_VALUE
        for (city in candidates) {
            val d = distanceKm(latitude, longitude, city.latitude, city.longitude)
            if (d <= city.radiusKm && d < bestKm) {
                best = city
                bestKm = d
            }
        }
        return best
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
