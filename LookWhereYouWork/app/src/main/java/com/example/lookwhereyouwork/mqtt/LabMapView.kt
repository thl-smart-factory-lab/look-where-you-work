package com.example.lookwhereyouwork.ui.mqtt

import android.content.Context
import android.graphics.*
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

    // Live Pose (Weltkoordinaten in Meter, passend zu deinen TikZ Zahlen)
    private var poseX: Float? = null
    private var poseY: Float? = null
    private var yawDeg: Float = 0f // 2D: nur yaw

    private var fovHalfDeg: Float = 30f
    private var selectedLabel: String? = null

    fun setFovHalfDegrees(v: Float) {
        fovHalfDeg = v
        invalidate()
    }

    fun setSelectedPrinter(label: String?) {
        selectedLabel = label
        invalidate()
    }

    // Zeichenparameter
    private val padPx = 36f
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

    // Welt-Bounds (auto)
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

        // bisschen Luft
        val m = 0.6f
        minX -= m; maxX += m
        minY -= m; maxY += m
    }

    // Welt -> Screen transform
    private fun worldToScreen(p: Pt2): PointF {
        val w = width.toFloat()
        val h = height.toFloat()

        val sx = (w - 2 * padPx) / (maxX - minX).coerceAtLeast(0.001f)
        val sy = (h - 2 * padPx) / (maxY - minY).coerceAtLeast(0.001f)

        // gleiche Skalierung in x/y, sonst werden Kreise zu Ellipsen
        val s = min(sx, sy)

        val x = padPx + (p.x - minX) * s
        // Y-Achse im Canvas geht nach unten, Welt-Y soll nach oben: invertieren
        val y = h - padPx - (p.y - minY) * s
        return PointF(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // Hintergrund
        canvas.drawColor(Color.WHITE)

        drawGrid(canvas)
        drawAxes(canvas)
        drawAnchors(canvas)
        drawPrinters(canvas)
        drawPoseAndHeading(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        // grobe Gridline pro 1m in Weltkoordinaten
        val step = 1f
        var x = kotlin.math.floor(minX / step) * step
        while (x <= maxX) {
            val p1 = worldToScreen(Pt2(x, minY))
            val p2 = worldToScreen(Pt2(x, maxY))
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, gridPaint)
            x += step
        }
        var y = kotlin.math.floor(minY / step) * step
        while (y <= maxY) {
            val p1 = worldToScreen(Pt2(minX, y))
            val p2 = worldToScreen(Pt2(maxX, y))
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, gridPaint)
            y += step
        }

        for (d in LabGeometry.printers) {
            val sp = worldToScreen(d.p)
            val paint = if (d.label == selectedLabel) selectedPrinterPaint else printerPaint
            canvas.drawRect(sp.x - 16f, sp.y - 16f, sp.x + 16f, sp.y + 16f, paint)
            canvas.drawText(d.label, sp.x + 20f, sp.y + 12f, textPaint)
        }
    }

    private fun drawAxes(canvas: Canvas) {
        // Achsen: x unten, y links (nur zur Orientierung)
        val origin = worldToScreen(Pt2(minX, minY))
        val xEnd = worldToScreen(Pt2(maxX, minY))
        val yEnd = worldToScreen(Pt2(minX, maxY))

        canvas.drawLine(origin.x, origin.y, xEnd.x, xEnd.y, axisPaint)
        canvas.drawLine(origin.x, origin.y, yEnd.x, yEnd.y, axisPaint)

        canvas.drawText("x", xEnd.x - 24f, xEnd.y - 12f, textPaint)
        canvas.drawText("y", yEnd.x + 12f, yEnd.y + 36f, textPaint)
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
            // kleiner “Block”
            canvas.drawRect(sp.x - 16f, sp.y - 16f, sp.x + 16f, sp.y + 16f, printerPaint)
            canvas.drawText(d.label, sp.x + 20f, sp.y + 12f, textPaint)
        }
    }

    private fun drawPoseAndHeading(canvas: Canvas) {
        val x = poseX ?: return
        val y = poseY ?: return

        val sp = worldToScreen(Pt2(x, y))
        canvas.drawCircle(sp.x, sp.y, 12f, posePaint)

        // Heading als Linie (z.B. 1.5 m in Weltkoordinaten)
        val len = 1.5f
        val yawWorld = -yawDeg + LabGeometry.yawOffsetDeg + LabGeometry.yawFlipDeg
        val rad = (yawWorld * Math.PI / 180.0).toFloat()

        // Konvention: yaw=0 zeigt nach +x, yaw=90 nach +y
        val hx = x + len * cos(rad)
        val hy = y + len * sin(rad)

        val sp2 = worldToScreen(Pt2(hx, hy))
        canvas.drawLine(sp.x, sp.y, sp2.x, sp2.y, headingPaint)

        // Text oben links
        canvas.drawText("pos=(${fmt(x)}, ${fmt(y)})  yaw=${fmt(yawDeg)}°", padPx, padPx, textPaint)

        val leftRad  = ((yawWorld - fovHalfDeg) * Math.PI / 180.0).toFloat()
        val rightRad = ((yawWorld + fovHalfDeg) * Math.PI / 180.0).toFloat()

        val coneLen = 2.5f
        val lEnd = worldToScreen(Pt2(x + coneLen * cos(leftRad),  y + coneLen * sin(leftRad)))
        val rEnd = worldToScreen(Pt2(x + coneLen * cos(rightRad), y + coneLen * sin(rightRad)))

        canvas.drawLine(sp.x, sp.y, lEnd.x, lEnd.y, headingPaint)
        canvas.drawLine(sp.x, sp.y, rEnd.x, rEnd.y, headingPaint)
    }

    private fun fmt(v: Float): String = String.format("%.2f", v)
}