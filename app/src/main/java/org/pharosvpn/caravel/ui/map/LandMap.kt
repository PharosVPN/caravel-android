// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pharosvpn.caravel.model.GeoCoord
import org.pharosvpn.caravel.ui.theme.Control
import org.pharosvpn.caravel.ui.theme.Teal
import kotlin.math.hypot
import kotlin.math.min

private val OceanTop = Color(0xFF0D121C)
private val OceanBottom = Color(0xFF080B12)
private val LandFill = Color(0xFF1C2636)
private val LandStroke = Color(0xFF334860)
private val Graticule = Color(0x0AFFFFFF)
private val ConnectedGreen = Color(0xFF49D17F)

/**
 * LandMap is the signature view — a Compose Canvas port of caravel-mac/LandMap:
 * Natural Earth land + graticule, screen-space "bowed" arcs (dashed data-plane,
 * solid control-plane), flowing traffic dots + pulsing pins while connected, and
 * pinch/pan zoom. Maroon/cream/teal styling.
 */
@Composable
fun LandMap(
    pins: List<MapPin>,
    arcs: List<MapArc>,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val geo = remember { WorldGeometry.load(context) }
    val measurer = rememberTextMeasurer()

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val minZoom = 1f
    val maxZoom = 12f

    // Animation clock for flow dots + pulses (only ticks when connected).
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(connected) {
        if (connected) {
            while (true) { withFrameMillis { timeMs = it } }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(minZoom, maxZoom)
                        val mx = maxOf(0f, (zoom - 1f) * size.width * 0.6f)
                        val my = maxOf(0f, (zoom - 1f) * size.height * 0.6f)
                        panX = (panX + pan.x).coerceIn(-mx, mx)
                        panY = (panY + pan.y).coerceIn(-my, my)
                    }
                },
        ) {
            val t = timeMs / 1000.0
            drawMap(geo, measurer, pins, arcs, connected, zoom, panX, panY, t)
        }

        Legend(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
        )
        ZoomControls(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            onIn = { zoom = (zoom * 1.6f).coerceIn(minZoom, maxZoom) },
            onOut = { zoom = (zoom / 1.6f).coerceIn(minZoom, maxZoom) },
            onReset = { zoom = 1f; panX = 0f; panY = 0f },
        )
    }
}

private fun DrawScope.drawMap(
    geo: WorldGeometry,
    measurer: TextMeasurer,
    pins: List<MapPin>,
    arcs: List<MapArc>,
    connected: Boolean,
    zoom: Float,
    panX: Float,
    panY: Float,
    t: Double,
) {
    val canvasSize = Size(size.width, size.height)
    val base = geo.fit(canvasSize)
    val z = zoom.toDouble()
    val cx = size.width / 2.0
    val cy = size.height / 2.0
    val fit = WorldGeometry.Fit(
        s = base.s * z,
        tx = cx + (base.tx - cx) * z + panX,
        ty = cy + (base.ty - cy) * z + panY,
    )

    // ocean
    drawRect(
        brush = Brush.verticalGradient(listOf(OceanTop, OceanBottom)),
        size = canvasSize,
    )

    // graticule
    val grat = Path()
    var lon = -180.0
    while (lon <= 180.0) {
        var first = true
        var lat = -80.0
        while (lat <= 80.0) {
            val p = geo.project(GeoCoord(lat, lon), fit)
            if (first) { grat.moveTo(p.x, p.y); first = false } else grat.lineTo(p.x, p.y)
            lat += 4.0
        }
        lon += 30.0
    }
    var glat = -60.0
    while (glat <= 60.0) {
        var first = true
        var glon = -180.0
        while (glon <= 180.0) {
            val p = geo.project(GeoCoord(glat, glon), fit)
            if (first) { grat.moveTo(p.x, p.y); first = false } else grat.lineTo(p.x, p.y)
            glon += 4.0
        }
        glat += 30.0
    }
    drawPath(grat, Graticule, style = Stroke(width = 0.6f))

    // land
    val land = Path()
    for (ring in geo.rings) {
        if (ring.size < 4) continue
        val p0 = geo.projectRaw(ring[0].toDouble(), ring[1].toDouble(), fit)
        land.moveTo(p0.x, p0.y)
        var i = 2
        while (i + 1 < ring.size) {
            val p = geo.projectRaw(ring[i].toDouble(), ring[i + 1].toDouble(), fit)
            land.lineTo(p.x, p.y)
            i += 2
        }
        land.close()
    }
    drawPath(land, LandFill)
    drawPath(land, LandStroke, style = Stroke(width = 0.5f))

    // arcs — each bowed away from the pin centroid so a chain splays out.
    val pinPts = pins.map { geo.project(it.coord, fit) }
    val centroid = if (pinPts.isEmpty()) Offset(size.width / 2, size.height / 2)
    else Offset(pinPts.map { it.x }.average().toFloat(), pinPts.map { it.y }.average().toFloat())

    for (arc in arcs) {
        val projected = arc.points.map { geo.project(it, fit) }
        val a = projected.firstOrNull() ?: continue
        val b = projected.lastOrNull() ?: continue
        val screen = bowedArc(a, b, centroid)
        val color = if (arc.style == ArcStyle.ControlPlane) Control else if (connected) ConnectedGreen else Teal
        val path = Path().apply {
            moveTo(screen[0].x, screen[0].y)
            for (k in 1 until screen.size) lineTo(screen[k].x, screen[k].y)
        }
        val effect = if (arc.style == ArcStyle.DataPlane) PathEffect.dashPathEffect(floatArrayOf(4f, 6f)) else null
        drawPath(path, color.copy(alpha = 0.85f), style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = effect))

        if (connected) {
            val lengths = cumulativeLengths(screen)
            val total = lengths.lastOrNull() ?: 0f
            if (total > 1f) {
                val dots = 3
                for (k in 0 until dots) {
                    val frac = ((t * 0.18) + k.toDouble() / dots) % 1.0
                    val p = pointAt(frac.toFloat(), screen, lengths, total)
                    drawCircle(color, radius = 2.4f, center = p)
                }
            }
        }
    }

    // pins
    for ((idx, pin) in pins.withIndex()) {
        drawPin(measurer, pinPts[idx], pin, connected, t)
    }
}

/** Quadratic-bezier arc bowing perpendicular to the chord, away from [center]. */
private fun bowedArc(a: Offset, b: Offset, center: Offset, steps: Int = 48): List<Offset> {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len <= 1f) return listOf(a, b)
    var nx = -dy / len
    var ny = dx / len
    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    if (nx * (mid.x - center.x) + ny * (mid.y - center.y) < 0) { nx = -nx; ny = -ny }
    val bow = min(len * 0.20f, 110f)
    val c = Offset(mid.x + nx * bow, mid.y + ny * bow)
    val out = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val u = i.toFloat() / steps
        val v = 1 - u
        out.add(
            Offset(
                v * v * a.x + 2 * v * u * c.x + u * u * b.x,
                v * v * a.y + 2 * v * u * c.y + u * u * b.y,
            ),
        )
    }
    return out
}

private fun DrawScope.drawPin(
    measurer: TextMeasurer,
    p: Offset,
    pin: MapPin,
    connected: Boolean,
    t: Double,
) {
    val color = when (pin.kind) {
        PinKind.Client -> Teal
        PinKind.Controller, PinKind.Relay -> Control
        PinKind.Node -> if (connected) ConnectedGreen else if (pin.active) Teal else Color.Gray
    }
    // pulsing ring while connected
    if (connected) {
        val phase = (t * 0.8) % 1.0
        val r = (8 + phase * 18).toFloat()
        drawCircle(color.copy(alpha = (0.5 * (1 - phase)).toFloat()), radius = r, center = p, style = Stroke(width = 1.5f))
    }
    // glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = 0.45f), Color.Transparent),
            center = p, radius = 15f,
        ),
        radius = 15f, center = p,
    )
    // dot
    val r = if (pin.kind == PinKind.Client) 4f else 5f
    drawCircle(color, radius = r, center = p)
    drawCircle(Color.White.copy(alpha = 0.9f), radius = r, center = p, style = Stroke(width = 1.4f))
    // label
    val layout = measurer.measure(
        pin.label,
        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
    )
    drawText(
        layout,
        topLeft = Offset(p.x - layout.size.width / 2f, p.y - 17f - layout.size.height),
    )
}

private fun cumulativeLengths(pts: List<Offset>): FloatArray {
    val out = FloatArray(pts.size)
    for (i in 1 until pts.size) {
        out[i] = out[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
    }
    return out
}

private fun pointAt(frac: Float, pts: List<Offset>, lengths: FloatArray, total: Float): Offset {
    val target = frac * total
    for (i in 1 until pts.size) {
        if (lengths[i] >= target) {
            val seg = lengths[i] - lengths[i - 1]
            val u = if (seg > 0) (target - lengths[i - 1]) / seg else 0f
            return Offset(
                pts[i - 1].x + (pts[i].x - pts[i - 1].x) * u,
                pts[i - 1].y + (pts[i].y - pts[i - 1].y) * u,
            )
        }
    }
    return pts.last()
}
