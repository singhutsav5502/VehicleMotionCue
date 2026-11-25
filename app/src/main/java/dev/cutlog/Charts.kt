package dev.cutlog

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private fun labelPaint(sizePx: Float) = Paint().apply {
    color = Muted.toArgb(); textSize = sizePx; isAntiAlias = true; textAlign = Paint.Align.CENTER
}

/** Daily bars with a dashed target line. Null = not logged. */
@Composable
fun BarChart(values: List<Int?>, labels: List<String>, target: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val paint = labelPaint(11.dp.toPx())
        val bottomPad = 20.dp.toPx()
        val topPad = 14.dp.toPx()
        val h = size.height - bottomPad - topPad
        val max = maxOf(target * 1.25f, (values.filterNotNull().maxOrNull() ?: 0).toFloat())
        val slot = size.width / values.size
        val barW = slot * 0.55f
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            if (v != null) {
                val bh = h * (v / max)
                val c = when {
                    v > target * 1.05f -> Over
                    v > target * 0.9f -> Warn
                    else -> Accent
                }
                drawRoundRect(c, Offset(cx - barW / 2, topPad + h - bh), Size(barW, bh), CornerRadius(4.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(v.toString(), cx, topPad + h - bh - 3.dp.toPx(), paint)
            } else {
                drawRoundRect(Color(0xFF1C221E), Offset(cx - barW / 2, topPad + h - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(2f))
            }
            drawContext.canvas.nativeCanvas.drawText(labels[i], cx, size.height - 4.dp.toPx(), paint)
        }
        val ty = topPad + h - h * (target / max)
        drawLine(
            Color.White.copy(alpha = 0.5f), Offset(0f, ty), Offset(size.width, ty), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}

/** Weight dots, moving-average line and optional dashed goal line, x by day. */
@Composable
fun WeightChart(points: List<Pair<Long, Float>>, avg: List<Pair<Long, Float>>, goal: Float?, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val range = remember(points, goal) {
        val ys = points.map { it.second } + listOfNotNull(goal)
        (ys.min() - 1f) to (ys.max() + 1f)
    }
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val paint = labelPaint(11.dp.toPx()).apply { textAlign = Paint.Align.LEFT }
        val left = 34.dp.toPx(); val bottom = 8.dp.toPx(); val top = 8.dp.toPx()
        val w = size.width - left; val h = size.height - top - bottom
        val x0 = points.first().first; val x1 = maxOf(points.last().first, x0 + 1)
        val (yMin, yMax) = range
        fun x(d: Long) = left + w * ((d - x0).toFloat() / (x1 - x0))
        fun y(v: Float) = top + h * (1 - (v - yMin) / (yMax - yMin))

        // gridlines
        for (k in 0..3) {
            val v = yMin + (yMax - yMin) * k / 3f
            val yy = y(v)
            drawLine(Color(0xFF1C221E), Offset(left, yy), Offset(size.width, yy), 1f)
            drawContext.canvas.nativeCanvas.drawText(fmt1(v), 0f, yy + 4.dp.toPx(), paint)
        }
        if (goal != null && goal in yMin..yMax) {
            drawLine(
                Accent.copy(alpha = 0.6f), Offset(left, y(goal)), Offset(size.width, y(goal)), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
        points.forEach { (d, v) -> drawCircle(Muted, 3.dp.toPx(), Offset(x(d), y(v))) }
        if (avg.size >= 2) {
            val path = Path()
            avg.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
            drawPath(path, Accent, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}
