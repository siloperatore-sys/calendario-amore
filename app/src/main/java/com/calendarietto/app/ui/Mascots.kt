package com.calendarietto.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.calendarietto.app.data.Mascot

enum class Mood { HAPPY, SLEEPY, EXCITED }

/** Le due mascotte, disegnate a mano (niente immagini): unicorno e robottino originale. */
@Composable
fun MascotView(mascot: Mascot, modifier: Modifier = Modifier, mood: Mood = Mood.HAPPY) {
    Canvas(modifier) {
        val g = Grid(this)
        if (mascot == Mascot.ROBOT) drawRobot(g, mood) else drawUnicorn(g, mood)
    }
}

/** Disegno in un quadrato 100x100 centrato. */
private class Grid(scope: DrawScope) {
    val u = scope.size.minDimension / 100f
    private val ox = (scope.size.width - 100f * u) / 2f
    private val oy = (scope.size.height - 100f * u) / 2f
    fun o(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)
    fun path(vararg pts: Pair<Float, Float>): Path = Path().apply {
        pts.forEachIndexed { i, (x, y) ->
            val p = o(x, y)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
}

private fun DrawScope.oval(g: Grid, cx: Float, cy: Float, w: Float, h: Float, color: Color) =
    drawOval(color, topLeft = g.o(cx - w / 2f, cy - h / 2f), size = g.sz(w, h))

private fun DrawScope.circle(g: Grid, cx: Float, cy: Float, r: Float, color: Color) =
    drawCircle(color, radius = r * g.u, center = g.o(cx, cy))

private fun DrawScope.smile(g: Grid, cx: Float, cy: Float, w: Float, color: Color, stroke: Float = 2.4f) =
    drawArc(
        color, startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = g.o(cx - w / 2f, cy - w / 2f), size = g.sz(w, w),
        style = Stroke(width = stroke * g.u, cap = StrokeCap.Round),
    )

private fun DrawScope.closedEye(g: Grid, cx: Float, cy: Float, color: Color) =
    drawArc(
        color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = g.o(cx - 4.5f, cy - 2f), size = g.sz(9f, 9f),
        style = Stroke(width = 2.4f * g.u, cap = StrokeCap.Round),
    )

private fun DrawScope.drawUnicorn(g: Grid, mood: Mood) {
    val ink = Color(0xFF4A3B5C)
    val pink = Color(0xFFFFB3D1)
    val lilac = Color(0xFFC9B6F2)
    val mint = Color(0xFFA8E6CF)
    val outline = Color(0xFFB79CED)

    // criniera dietro
    circle(g, 29f, 34f, 12f, pink)
    circle(g, 22f, 48f, 11f, lilac)
    circle(g, 23f, 63f, 10f, mint)
    circle(g, 71f, 36f, 9f, lilac)

    // orecchie
    drawPath(g.path(33f to 33f, 37f to 14f, 47f to 27f), Color.White)
    drawPath(g.path(36f to 29f, 38.5f to 19f, 43f to 26f), pink)
    drawPath(g.path(53f to 27f, 63f to 14f, 67f to 33f), Color.White)
    drawPath(g.path(57f to 26f, 61.5f to 19f, 64f to 29f), pink)

    // testa
    circle(g, 50f, 56f, 30f, Color.White)
    drawCircle(outline, radius = 30f * g.u, center = g.o(50f, 56f), style = Stroke(width = 2.2f * g.u))

    // corno con le righine
    drawPath(g.path(44f to 29f, 56f to 29f, 50f to 3f), Color(0xFFFFE29A))
    drawLine(Color(0xFFF5C46B), g.o(45.5f, 23f), g.o(53.5f, 20f), strokeWidth = 1.6f * g.u, cap = StrokeCap.Round)
    drawLine(Color(0xFFF5C46B), g.o(47f, 15f), g.o(52.5f, 13f), strokeWidth = 1.6f * g.u, cap = StrokeCap.Round)

    // ciuffi davanti
    circle(g, 39f, 31f, 8f, pink)
    circle(g, 62f, 32f, 7f, mint)
    circle(g, 33f, 38f, 5.5f, lilac)

    // occhi
    when (mood) {
        Mood.SLEEPY -> {
            closedEye(g, 39f, 58f, ink); closedEye(g, 61f, 58f, ink)
        }
        else -> {
            oval(g, 39f, 57f, 7.5f, 9.5f, ink)
            oval(g, 61f, 57f, 7.5f, 9.5f, ink)
            circle(g, 40.6f, 54.6f, 1.8f, Color.White)
            circle(g, 62.6f, 54.6f, 1.8f, Color.White)
            circle(g, 38f, 59.5f, 0.9f, Color.White)
            circle(g, 60f, 59.5f, 0.9f, Color.White)
        }
    }
    // guance
    oval(g, 30f, 67f, 9f, 5f, pink)
    oval(g, 70f, 67f, 9f, 5f, pink)
    // bocca
    if (mood == Mood.EXCITED) oval(g, 50f, 69f, 6f, 6.5f, Color(0xFFE77FA8))
    else smile(g, 50f, 63.5f, 8f, ink)
}

private fun DrawScope.drawRobot(g: Grid, mood: Mood) {
    val navy = Color(0xFF4A5AA8)
    val screen = Color(0xFF2B3150)
    val glow = Color(0xFFA8E6CF)
    val shell = Color(0xFFB8DBFA)
    val bulb = Color(0xFFFFD98E)

    // antenna
    drawLine(navy, g.o(50f, 22f), g.o(50f, 10f), strokeWidth = 2.6f * g.u, cap = StrokeCap.Round)
    circle(g, 50f, 8f, 5.5f, bulb)
    circle(g, 48.4f, 6.4f, 1.6f, Color.White)

    // orecchie-bulloni
    drawRoundRect(Color(0xFF8EC9F5), topLeft = g.o(12f, 44f), size = g.sz(10f, 20f), cornerRadius = CornerRadius(4f * g.u))
    drawRoundRect(Color(0xFF8EC9F5), topLeft = g.o(78f, 44f), size = g.sz(10f, 20f), cornerRadius = CornerRadius(4f * g.u))

    // testa
    drawRoundRect(shell, topLeft = g.o(19f, 22f), size = g.sz(62f, 62f), cornerRadius = CornerRadius(18f * g.u))
    drawRoundRect(
        navy, topLeft = g.o(19f, 22f), size = g.sz(62f, 62f), cornerRadius = CornerRadius(18f * g.u),
        style = Stroke(width = 2.4f * g.u),
    )
    // schermo
    drawRoundRect(screen, topLeft = g.o(27f, 33f), size = g.sz(46f, 36f), cornerRadius = CornerRadius(11f * g.u))

    // occhi luminosi
    when (mood) {
        Mood.SLEEPY -> {
            closedEye(g, 40f, 50f, glow); closedEye(g, 60f, 50f, glow)
        }
        else -> {
            drawRoundRect(glow, topLeft = g.o(36f, 44f), size = g.sz(8f, 10f), cornerRadius = CornerRadius(4f * g.u))
            drawRoundRect(glow, topLeft = g.o(56f, 44f), size = g.sz(8f, 10f), cornerRadius = CornerRadius(4f * g.u))
            circle(g, 42f, 46f, 1.4f, Color.White)
            circle(g, 62f, 46f, 1.4f, Color.White)
        }
    }
    // bocca
    if (mood == Mood.EXCITED) oval(g, 50f, 62f, 7f, 5f, glow)
    else smile(g, 50f, 55.5f, 9f, glow)

    // guance e cuoricino
    oval(g, 31f, 76f, 9f, 4.5f, Color(0xFFFFC9D6))
    oval(g, 69f, 76f, 9f, 4.5f, Color(0xFFFFC9D6))
    circle(g, 48.4f, 76f, 2.2f, Color(0xFFFF9EB8))
    circle(g, 51.6f, 76f, 2.2f, Color(0xFFFF9EB8))
    drawPath(g.path(46.4f to 76.6f, 53.6f to 76.6f, 50f to 80.5f), Color(0xFFFF9EB8))
}
