// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.model

/**
 * Regions maps a node's region code to coordinates entirely offline — the same
 * table the macOS client ships (Regions.swift), so a node lands on its city
 * without any IP-geolocation lookup. Unknown regions return null (no pin).
 */
object Regions {
    private data class Place(val coord: GeoCoord, val city: String)

    private val table: Map<String, Place> = mapOf(
        // DigitalOcean regions (+ a few common provider cities).
        "nyc1" to Place(GeoCoord(40.71, -74.01), "New York"),
        "nyc2" to Place(GeoCoord(40.71, -74.01), "New York"),
        "nyc3" to Place(GeoCoord(40.71, -74.01), "New York"),
        "sfo1" to Place(GeoCoord(37.77, -122.42), "San Francisco"),
        "sfo2" to Place(GeoCoord(37.77, -122.42), "San Francisco"),
        "sfo3" to Place(GeoCoord(37.77, -122.42), "San Francisco"),
        "tor1" to Place(GeoCoord(43.65, -79.38), "Toronto"),
        "ams2" to Place(GeoCoord(52.37, 4.90), "Amsterdam"),
        "ams3" to Place(GeoCoord(52.37, 4.90), "Amsterdam"),
        "lon1" to Place(GeoCoord(51.51, -0.13), "London"),
        "fra1" to Place(GeoCoord(50.11, 8.68), "Frankfurt"),
        "sgp1" to Place(GeoCoord(1.35, 103.82), "Singapore"),
        "blr1" to Place(GeoCoord(12.97, 77.59), "Bangalore"),
        "syd1" to Place(GeoCoord(-33.87, 151.21), "Sydney"),
        // Bare country / city codes that may appear in a region field.
        "us" to Place(GeoCoord(39.0, -98.0), "United States"),
        "eu" to Place(GeoCoord(50.0, 9.0), "Europe"),
        "nl" to Place(GeoCoord(52.37, 4.90), "Netherlands"),
        "de" to Place(GeoCoord(51.0, 9.0), "Germany"),
        "gb" to Place(GeoCoord(51.51, -0.13), "United Kingdom"),
        "uk" to Place(GeoCoord(51.51, -0.13), "United Kingdom"),
        "sg" to Place(GeoCoord(1.35, 103.82), "Singapore"),
        "in" to Place(GeoCoord(20.6, 78.96), "India"),
        "au" to Place(GeoCoord(-33.87, 151.21), "Australia"),
        "ca" to Place(GeoCoord(43.65, -79.38), "Canada"),
    )

    data class Located(val coord: GeoCoord, val city: String)

    /** locate returns the coordinate + a display city for a region code, or null. */
    fun locate(region: String?): Located? {
        val r = region?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        table[r]?.let { return Located(it.coord, it.city) }
        // A region like "eu-nl" → try the trailing country, then the leading area.
        val parts = r.split("-")
        for (p in parts.asReversed()) {
            table[p]?.let { return Located(it.coord, it.city) }
        }
        return null
    }
}
