package com.example.lookwhereyouwork.geometry

data class Pt2(val x: Float, val y: Float)

data class Anchor(val id: String, val p: Pt2)
data class Printer(val id: String, val label: String, val p: Pt2)

object LabGeometry {

    val yawFlipDeg: Float = 0f

    // Referenzpunkte (Anker)
    val anchors: List<Anchor> = listOf(
        Anchor("A", Pt2(-1.440f, 0.278f)),
        Anchor("B", Pt2( 5.591f, 1.000f)),
        Anchor("C", Pt2( 0.400f,11.926f)),
        Anchor("D", Pt2( 5.502f,11.633f)),
    )

    // Drucker
    val printers: List<Printer> = listOf(
        Printer("pA", "p_A", Pt2(0.521f, 4.6f)),
        Printer("pB", "p_B", Pt2(0.521f, 3.4f)),
        Printer("pC", "p_C", Pt2(0.521f, 2.2f)),
        Printer("pD", "p_D", Pt2(5.585f, 3.006f)),

    )

    // Für Auto-Scaling im View: Bounding Box aus allen Punkten
    fun allPoints(): List<Pt2> =
        anchors.map { it.p } + printers.map { it.p }

    private fun angleDeg(from: Pt2, to: Pt2): Float {
        val dx = (to.x - from.x).toDouble()
        val dy = (to.y - from.y).toDouble()
        return Math.toDegrees(kotlin.math.atan2(dy, dx)).toFloat()
    }

    val yawOffsetDeg: Float by lazy {
        val a = anchors.first { it.id == "A" }.p
        val b = anchors.first { it.id == "B" }.p
        val d = anchors.first { it.id == "D" }.p

        val angleAB = angleDeg(a, b)
        val angleBD = angleDeg(b, d)
        angleAB - angleBD
    }
}

