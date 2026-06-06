// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.pharosvpn.caravel.model.GeoCoord
import kotlin.math.PI
import kotlin.math.cos

/**
 * WorldGeometry loads the offline land outline (assets/land.geojson) and projects
 * it with Natural Earth 1 — a 1:1 port of caravel-mac/LandMap.swift's
 * WorldGeometry. Coordinates are pre-projected into an unscaled world space; the
 * map fits + zooms them per frame.
 */
class WorldGeometry private constructor(
    val rings: List<FloatArray>, // each: [x0,y0,x1,y1,...] in projected world space
    val minX: Double, val maxX: Double, val minY: Double, val maxY: Double,
) {
    data class Fit(val s: Double, val tx: Double, val ty: Double)

    fun fit(size: Size, pad: Double = 18.0): Fit {
        val bw = maxX - minX
        val bh = maxY - minY
        if (bw <= 0 || bh <= 0) return Fit(1.0, 0.0, 0.0)
        val s = minOf((size.width - 2 * pad) / bw, (size.height - 2 * pad) / bh)
        return Fit(s, size.width / 2 - s * (minX + maxX) / 2, size.height / 2 + s * (minY + maxY) / 2)
    }

    fun projectRaw(x: Double, y: Double, f: Fit): Offset =
        Offset((f.s * x + f.tx).toFloat(), (f.ty - f.s * y).toFloat())

    fun project(c: GeoCoord, f: Fit): Offset {
        val (x, y) = naturalEarth1(c.lon, c.lat)
        return projectRaw(x, y, f)
    }

    companion object {
        @Volatile private var cached: WorldGeometry? = null

        fun load(context: Context): WorldGeometry =
            cached ?: synchronized(this) {
                cached ?: parse(context).also { cached = it }
            }

        fun naturalEarth1(lonDeg: Double, latDeg: Double): Pair<Double, Double> {
            val lambda = lonDeg * PI / 180
            val phi = latDeg * PI / 180
            val phi2 = phi * phi
            val phi4 = phi2 * phi2
            val x = lambda * (0.8707 - 0.131979 * phi2 + phi4 * (-0.013791 + phi4 * (0.003971 * phi2 - 0.001529 * phi4)))
            val y = phi * (1.007226 + phi2 * (0.015085 + phi4 * (-0.044475 + 0.028874 * phi2 - 0.005916 * phi4)))
            return x to y
        }

        private fun parse(context: Context): WorldGeometry {
            val rings = ArrayList<FloatArray>()
            var mnX = Double.MAX_VALUE; var mxX = -Double.MAX_VALUE
            var mnY = Double.MAX_VALUE; var mxY = -Double.MAX_VALUE
            try {
                val text = context.assets.open("land.geojson").bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(text).jsonObject
                val features = obj["features"]?.jsonArray ?: JsonArray(emptyList())
                for (fe in features) {
                    val geom = fe.jsonObject["geometry"]?.jsonObject ?: continue
                    val type = geom["type"]?.jsonPrimitive?.content
                    val coords = geom["coordinates"]?.jsonArray ?: continue
                    val polys: List<JsonArray> = when (type) {
                        "Polygon" -> listOf(coords)
                        "MultiPolygon" -> coords.map { it.jsonArray }
                        else -> emptyList()
                    }
                    for (poly in polys) {
                        for (ringEl in poly) {
                            val ring = ringEl.jsonArray
                            val pts = ArrayList<Float>(ring.size * 2)
                            for (pEl in ring) {
                                val p = pEl.jsonArray
                                if (p.size < 2) continue
                                val lon = p[0].jsonPrimitive.content.toDouble()
                                val lat = p[1].jsonPrimitive.content.toDouble()
                                val (x, y) = naturalEarth1(lon, lat)
                                pts.add(x.toFloat()); pts.add(y.toFloat())
                                if (x < mnX) mnX = x; if (x > mxX) mxX = x
                                if (y < mnY) mnY = y; if (y > mxY) mxY = y
                            }
                            if (pts.size > 4) rings.add(pts.toFloatArray())
                        }
                    }
                }
            } catch (_: Throwable) {
                // No land outline — the map still draws ocean + graticule + pins.
            }
            if (mnX > mxX) { mnX = 0.0; mxX = 0.0; mnY = 0.0; mxY = 0.0 }
            return WorldGeometry(rings, mnX, mxX, mnY, mxY)
        }
    }
}

/** greatCircle interpolates the shortest path on the sphere (lon/lat degrees). */
fun greatCircle(a: GeoCoord, b: GeoCoord, steps: Int = 64): List<GeoCoord> {
    val lat1 = a.lat * PI / 180; val lon1 = a.lon * PI / 180
    val lat2 = b.lat * PI / 180; val lon2 = b.lon * PI / 180
    val x1 = cos(lat1) * cos(lon1); val y1 = cos(lat1) * Math.sin(lon1); val z1 = Math.sin(lat1)
    val x2 = cos(lat2) * cos(lon2); val y2 = cos(lat2) * Math.sin(lon2); val z2 = Math.sin(lat2)
    val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
    val omega = Math.acos(dot)
    if (omega < 1e-6) return listOf(a, b)
    val sinO = Math.sin(omega)
    val out = ArrayList<GeoCoord>(steps + 1)
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val s1 = Math.sin((1 - t) * omega) / sinO
        val s2 = Math.sin(t * omega) / sinO
        val x = s1 * x1 + s2 * x2; val y = s1 * y1 + s2 * y2; val z = s1 * z1 + s2 * z2
        out.add(GeoCoord(Math.atan2(z, Math.sqrt(x * x + y * y)) * 180 / PI, Math.atan2(y, x) * 180 / PI))
    }
    return out
}
