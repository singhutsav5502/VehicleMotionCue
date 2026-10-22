package com.motioncues.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import com.motioncues.motion.CueDynamics
import com.motioncues.settings.DotSize
import com.motioncues.settings.DotStyle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-screen, non-interactive view that draws two columns of dots down each side of the
 * screen and shifts them by the offset from [CueDynamics] every frame.
 *
 * The edges are used so the dots sit in peripheral vision and stay off the content the user
 * is reading. Each dot is light with a dark rim so it reads on both light and dark apps.
 */
@SuppressLint("ViewConstructor")
class MotionCuesOverlayView(
    context: Context,
    private val dynamics: CueDynamics,
) : View(context), Choreographer.FrameCallback {

    var style: DotStyle = DotStyle.REGULAR
        set(value) { field = value; invalidate() }

    var size: DotSize = DotSize.MEDIUM
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL
        color = Color.rgb(245, 245, 245)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(150, 0, 0, 0)
    }

    /** Resting dot centres, flattened as x0, y0, x1, y1, … */
    private var restingCenters = FloatArray(0)

    private var animating = false
    private var framePending = false
    private var lastFrameNanos = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun startAnimating() {
        if (animating) return
        animating = true
        lastFrameNanos = 0L
        requestFrame()
    }

    fun stopAnimating() {
        animating = false
        framePending = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    /**
     * Ask for a simulation step on the next vsync. Called on every sensor sample; frames keep
     * chaining only while the dots are moving, so a parked car costs no display frames.
     */
    fun requestFrame() {
        if (!animating || framePending) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePending = false
        if (!animating) return
        // After an idle gap, resume with one nominal frame rather than a huge jump.
        val gap = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
        val dt = if (gap <= 0L || gap > IDLE_GAP_NS) NOMINAL_FRAME_S else gap / 1e9f
        lastFrameNanos = frameTimeNanos
        if (dynamics.step(dt)) {
            invalidate()
            requestFrame()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimating()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        restingCenters = layoutDots(w.toFloat(), h.toFloat())
    }

    private fun layoutDots(w: Float, h: Float): FloatArray {
        if (w <= 0f || h <= 0f) return FloatArray(0)
        val spacing = ROW_SPACING_DP * density
        val inner = EDGE_MARGIN_DP * density
        val outer = inner + COLUMN_GAP_DP * density
        val vMargin = VERTICAL_MARGIN_DP * density
        val usable = max(0f, h - 2 * vMargin)
        val rows = max(2, (usable / spacing).roundToInt() + 1)
        val step = usable / (rows - 1)

        val points = ArrayList<Float>(rows * 8)
        for (side in 0..1) {
            val xInner = if (side == 0) inner else w - inner
            val xOuter = if (side == 0) outer else w - outer
            for (r in 0 until rows) {
                val y = vMargin + r * step
                points += xInner; points += y
                // Second column staggered by half a row reads as a lattice, not a ladder.
                if (r < rows - 1) { points += xOuter; points += y + step / 2f }
            }
        }
        return points.toFloatArray()
    }

    override fun onDraw(canvas: Canvas) {
        val activity = dynamics.activity
        val (alpha, scale) = when (style) {
            DotStyle.REGULAR -> 1f to 1f
            DotStyle.DYNAMIC -> activity to (0.6f + 0.4f * activity)
        }
        if (alpha <= 0.01f) return

        val radius = size.radiusDp * density * scale
        val a = (alpha * 255).roundToInt().coerceIn(0, 255)
        fill.alpha = a
        rim.alpha = (a * RIM_ALPHA_FRACTION).roundToInt()

        val dx = dynamics.offsetX
        val dy = dynamics.offsetY
        val c = restingCenters
        var i = 0
        while (i < c.size) {
            val x = c[i] + dx
            val y = c[i + 1] + dy
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, rim)
            i += 2
        }
    }

    private companion object {
        const val ROW_SPACING_DP = 56f
        const val EDGE_MARGIN_DP = 14f
        const val COLUMN_GAP_DP = 20f
        const val VERTICAL_MARGIN_DP = 40f
        const val RIM_ALPHA_FRACTION = 150f / 255f
        const val NOMINAL_FRAME_S = 1f / 60f
        const val IDLE_GAP_NS = 100_000_000L
    }
}
