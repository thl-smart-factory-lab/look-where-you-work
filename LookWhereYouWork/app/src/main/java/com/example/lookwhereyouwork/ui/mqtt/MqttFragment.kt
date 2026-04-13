package com.example.lookwhereyouwork.ui.mqtt

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lookwhereyouwork.R
import kotlinx.coroutines.launch
import android.content.Context
import android.hardware.SensorManager
import android.widget.Button
import kotlin.math.roundToInt

class MqttFragment : Fragment(R.layout.fragment_mqtt) {

    private val vm: MqttViewModel by viewModels()
    private lateinit var txtStatus: TextView
    private lateinit var recycler: RecyclerView
    private val adapter = TopicAdapter()

    private lateinit var txtGyro: TextView
    private lateinit var btnGyroReset: Button
    private lateinit var orientationTracker: OrientationTracker

    private lateinit var labMap: LabMapView
    private var lastPosX: Float? = null
    private var lastPosY: Float? = null
    private var lastYawDeg: Float = 0f

    private lateinit var txtLookAt: TextView
    private lateinit var txtLookDetails: TextView


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtStatus = view.findViewById(R.id.txtStatus)
        recycler = view.findViewById(R.id.recycler)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        labMap = view.findViewById(R.id.labMap)

        txtGyro = view.findViewById(R.id.txtGyro)
        btnGyroReset = view.findViewById(R.id.btnGyroReset)

        txtLookAt = view.findViewById(R.id.txtLookAt)
        txtLookDetails = view.findViewById(R.id.txtLookDetails)



        val sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        orientationTracker = OrientationTracker(sensorManager) { yaw, pitch, roll ->
            lastYawDeg = yaw
            requireActivity().runOnUiThread {
                txtGyro.text = "Orientation (Δ): yaw=${fmt(yaw)}°, pitch=${fmt(pitch)}°, roll=${fmt(roll)}°"
                updateInferenceAndUi()

                val x = lastPosX
                val y = lastPosY
                if (x != null && y != null) {
                    labMap.setPose(x, y, lastYawDeg)
                }
            }
        }

        btnGyroReset.setOnClickListener {
            orientationTracker.resetBaseline()
        }



        vm.start()

        viewLifecycleOwner.lifecycleScope.launch {
            vm.uiState.collect { state ->
                txtStatus.text = state.statusText
                adapter.submit(state.lastByTopic)

                // Neueste MQTT Message nehmen und Position parsen
                val newest = state.lastByTopic.values.maxByOrNull { it.timestampMs }
                if (newest != null) {
                    val xy = tryParsePositionXY(newest.payload)
                    if (xy != null) {
                        lastPosX = xy.first
                        lastPosY = xy.second
                        labMap.setPose(xy.first, xy.second, lastYawDeg)
                        updateInferenceAndUi()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationTracker.start()
    }

    override fun onStop() {
        orientationTracker.stop()
        super.onStop()
    }

    private fun updateInferenceAndUi() {
        val x = lastPosX ?: return
        val y = lastPosY ?: return

        // yawWorld muss genauso sein wie in LabMapView (Offset Richtung AB)
        val yawWorld = -lastYawDeg + com.example.lookwhereyouwork.geometry.LabGeometry.yawOffsetDeg + com.example.lookwhereyouwork.geometry.LabGeometry.yawFlipDeg
        val res = inferPrinter(x, y, yawWorld)

        if (res == null) {
            txtLookAt.text = "Looking at: -"
            txtLookDetails.text = "Δ: -\nDist: -\nFOV: ±${FOV_HALF_DEG}°"
            labMap.setSelectedPrinter(null)
        } else {
            txtLookAt.text = "Looking at: ${res.label}"
            txtLookDetails.text = "Δ: ${fmt(res.deltaDeg)}°\nDist: ${fmt(res.distance)} m\nFOV: ±${FOV_HALF_DEG}°"
            labMap.setSelectedPrinter(res.label)
        }

        labMap.setFovHalfDegrees(FOV_HALF_DEG)
        labMap.setPose(x, y, lastYawDeg) // LabMapView addiert intern den Offset fürs Zeichnen
    }

}

private fun fmt(x: Float): String {
    // 1 Nachkommastelle
    val v = (x * 10f).roundToInt() / 10f
    return v.toString()
}

private fun tryParsePositionXY(payload: String): Pair<Float, Float>? {
    // Beispiel:
    // position,tagName=uwb-a, positionX=2.07, positionY=2.02, positionZ=0
    val rxX = Regex("""\bpositionX\s*=\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    val rxY = Regex("""\bpositionY\s*=\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    val x = rxX.find(payload)?.groupValues?.get(1)?.toFloatOrNull()
    val y = rxY.find(payload)?.groupValues?.get(1)?.toFloatOrNull()

    return if (x != null && y != null) x to y else null
}

private const val FOV_HALF_DEG = 15f  // Δ_max (Halbwinkel)

private data class InferenceResult(
    val label: String,          // z.B. "p_A"
    val deltaDeg: Float,        // Δ in Grad (gewrappt)
    val distance: Float         // Abstand in m
)

private fun wrapTo180(deg: Float): Float {
    var a = deg
    while (a <= -180f) a += 360f
    while (a > 180f) a -= 360f
    return a
}

private fun inferPrinter(x: Float, y: Float, yawDegWorld: Float): InferenceResult? {
    var best: InferenceResult? = null

    for (p in com.example.lookwhereyouwork.geometry.LabGeometry.printers) {
        val dx = p.p.x - x
        val dy = p.p.y - y

        val bearingRad = kotlin.math.atan2(dy, dx)           // φ_i
        val bearingDeg = (bearingRad * 180f / Math.PI.toFloat())

        val delta = wrapTo180(bearingDeg - yawDegWorld)      // Δ_i = wrapToPi(φ_i - θ)
        val absDelta = kotlin.math.abs(delta)

        val dist = kotlin.math.sqrt(dx*dx + dy*dy)

        if (absDelta <= FOV_HALF_DEG) {
            val cand = InferenceResult(label = p.label, deltaDeg = delta, distance = dist)
            best = when {
                best == null -> cand
                kotlin.math.abs(cand.deltaDeg) < kotlin.math.abs(best!!.deltaDeg) -> cand
                kotlin.math.abs(cand.deltaDeg) == kotlin.math.abs(best!!.deltaDeg) && cand.distance < best!!.distance -> cand
                else -> best
            }
        }
    }

    return best
}




