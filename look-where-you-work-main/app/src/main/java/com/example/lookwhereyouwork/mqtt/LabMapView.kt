package com.example.lookwhereyouwork.ui.mqtt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.example.lookwhereyouwork.geometry.LabGeometry
import com.example.lookwhereyouwork.geometry.Pt2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LabMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var poseX: Float? = null
    private var poseY: Float? = null
    private var yawDeg: Float = 0f

    private var fovHalfDeg: Float = 30f
    private var selectedLabel: String? = null

    private var viewMinX = 0f
    private var viewMaxX = 1f
    private var viewMinY = 0f
    private var viewMaxY = 1f

    fun setFovHalfDegrees(v: Float) {
        fovHalfDeg = v
        invalidate()
    }

    fun setSelectedPrinter(label: String?) {
        selectedLabel = label
        invalidate()
    }

    private val padPx = 20f
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.DKGRAY
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.LTGRAY
        alpha = 160
    }
    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val printerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(30, 90, 200)
    }
    private val posePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 150, 60)
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.rgb(0, 150, 60)
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
    }
    private val selectedPrinterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(220, 60, 20)
    }

    private var minX = 0f
    private var maxX = 1f
    private var minY = 0f
    private var maxY = 1f

    init {
        recomputeWorldBounds()
    }

    fun setPose(x: Float, y: Float, yawDegrees: Float) {
        poseX = x
        poseY = y
        yawDeg = yawDegrees
        invalidate()
    }

    fun clearPose() {
        poseX = null
        poseY = null
        invalidate()
    }

    private fun recomputeWorldBounds() {
        val pts = LabGeometry.allPoints()
        minX = pts.minOf { it.x }
        maxX = pts.maxOf { it.x }
        minY = pts.minOf { it.y }
        maxY = pts.maxOf { it.y }

        val mx = 0.8f
        val my = 0.8f
        minX -= mx
        maxX += mx
        minY -= my
        maxY += my

        viewMinX = minX
        viewMaxX = maxX
        viewMinY = minY
        viewMaxY = maxY
    }

    private fun worldToScreen(p: Pt2): PointF {
        val w = width.toFloat()
        val h = height.toFloat()

        val contentW = (w - 2 * padPx).coerceAtLeast(1f)
        val contentH = (h - 2 * padPx).coerceAtLeast(1f)
        val worldW = (viewMaxX - viewMinX).coerceAtLeast(0.001f)
        val worldH = (viewMaxY - viewMinY).coerceAtLeast(0.001f)

        val sx = contentW / worldW
        val sy = contentH / worldH
        val s = min(sx, sy)

        val offsetX = padPx + (contentW - worldW * s) / 2f
        val offsetY = padPx + (contentH - worldH * s) / 2f

        val x = offsetX + (p.x - viewMinX) * s
        val y = h - offsetY - (p.y - viewMinY) * s
        return PointF(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        canvas.drawColor(Color.WHITE)

        drawGrid(canvas)
        drawAxes(canvas)
        drawAnchors(canvas)
        drawPrinters(canvas)
        drawPoseAndHeading(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val step = 1f

        var x = kotlin.math.floor(viewMinX / step) * step
        while (x <= viewMaxX) {
            val p1 = worldToScreen(Pt2(x, viewMinY))
            val p2 = worldToScreen(Pt2(x, viewMaxY))
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, gridPaint)
            x += step
        }

        var y = kotlin.math.floor(viewMinY / step) * step
        while (y <= viewMaxY) {
            val p1 = worldToScreen(Pt2(viewMinX, y))
            val p2 = worldToScreen(Pt2(viewMaxX, y))
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, gridPaint)
            y += step
        }
    }

    private fun drawAxes(canvas: Canvas) {
        val origin = worldToScreen(Pt2(viewMinX, viewMinY))
        val xEnd = worldToScreen(Pt2(viewMaxX, viewMinY))
        val yEnd = worldToScreen(Pt2(viewMinX, viewMaxY))

        canvas.drawLine(origin.x, origin.y, xEnd.x, xEnd.y, axisPaint)
        canvas.drawLine(origin.x, origin.y, yEnd.x, yEnd.y, axisPaint)
    }

    private fun drawAnchors(canvas: Canvas) {
        for (a in LabGeometry.anchors) {
            val sp = worldToScreen(a.p)
            canvas.drawCircle(sp.x, sp.y, 10f, anchorPaint)
            canvas.drawText(a.id, sp.x + 12f, sp.y - 12f, textPaint)
        }
    }

    private fun drawPrinters(canvas: Canvas) {
        for (d in LabGeometry.printers) {
            val sp = worldToScreen(d.p)
            val paint = if (d.label == selectedLabel) selectedPrinterPaint else printerPaint
            val halfSize = if (d.label == selectedLabel) 16f else 12f

            canvas.drawRect(sp.x - halfSize, sp.y - halfSize, sp.x + halfSize, sp.y + halfSize, paint)
            canvas.drawText(d.label, sp.x + halfSize + 8f, sp.y + 12f, textPaint)
        }
    }

    private fun drawPoseAndHeading(canvas: Canvas) {
        val x = poseX ?: return
        val y = poseY ?: return

        val sp = worldToScreen(Pt2(x, y))
        canvas.drawCircle(sp.x, sp.y, 12f, posePaint)

        val len = 1.5f
        val yawWorld = -yawDeg + LabGeometry.yawOffsetDeg + LabGeometry.yawFlipDeg
        val rad = (yawWorld * Math.PI / 180.0).toFloat()

        val hx = x + len * cos(rad)
        val hy = y + len * sin(rad)

        val sp2 = worldToScreen(Pt2(hx, hy))
        canvas.drawLine(sp.x, sp.y, sp2.x, sp2.y, headingPaint)

        val leftRad = ((yawWorld - fovHalfDeg) * Math.PI / 180.0).toFloat()
        val rightRad = ((yawWorld + fovHalfDeg) * Math.PI / 180.0).toFloat()

        val coneLen = 2.5f
        val lEnd = worldToScreen(Pt2(x + coneLen * cos(leftRad), y + coneLen * sin(leftRad)))
        val rEnd = worldToScreen(Pt2(x + coneLen * cos(rightRad), y + coneLen * sin(rightRad)))

        canvas.drawLine(sp.x, sp.y, lEnd.x, lEnd.y, headingPaint)
        canvas.drawLine(sp.x, sp.y, rEnd.x, rEnd.y, headingPaint)
    }
}
