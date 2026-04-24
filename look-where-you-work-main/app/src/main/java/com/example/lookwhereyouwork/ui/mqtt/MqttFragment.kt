package com.example.lookwhereyouwork.ui.mqtt

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.lookwhereyouwork.R
import com.example.lookwhereyouwork.model.TopicMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MqttFragment : Fragment(R.layout.fragment_mqtt) {

    private val vm: MqttViewModel by viewModels()

    private lateinit var txtStatus: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var debugPanel: LinearLayout
    private val adapter = TopicAdapter()

    private lateinit var txtGyro: TextView
    private lateinit var btnGyroReset: Button
    private lateinit var orientationTracker: OrientationTracker

    private lateinit var labMap: LabMapView
    private lateinit var txtLookAt: TextView
    private lateinit var txtLookDetails: TextView
    private lateinit var viewPager: ViewPager2

    private var txtPrinterTopic: TextView? = null
    private var txtPrinterPayload: TextView? = null

    private var lastPosX: Float? = null
    private var lastPosY: Float? = null

    private var lastYawDeg: Float = 0f
    private var lastPitchDeg: Float = 0f
    private var lastRollDeg: Float = 0f

    private var lastLookAtLabel: String? = null
    private var lastLookAtDeltaDeg: Float? = null
    private var lastLookAtDist: Float? = null
    private var latchedPrinterLabel: String? = null
    private var lastByTopic: Map<String, TopicMessage> = emptyMap()

    private var telemetryJob: Job? = null

    private val telemetryIntervalMs = 100L

    private val deviceClass by lazy { com.example.lookwhereyouwork.util.DeviceInfo.deviceClass() }
    private val deviceId by lazy { com.example.lookwhereyouwork.util.DeviceInfo.deviceId() }
    private val tagName: String = "uwb-a"
    private val telemetryTopic by lazy { "sf/telemetry/$deviceClass/$deviceId" }

    private val isFullUi by lazy { isPixelDevice() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        viewPager.offscreenPageLimit = 2
        viewPager.adapter = MqttPagerAdapter(
            onMapPageBound = ::bindMapPage,
            onPrinterPageBound = ::bindPrinterPage
        )

        val sensorManager =
            requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        orientationTracker = OrientationTracker(sensorManager) { yaw, pitch, roll ->
            lastYawDeg = yaw
            lastPitchDeg = pitch
            lastRollDeg = roll

            requireActivity().runOnUiThread {
                if (::txtGyro.isInitialized) {
                    txtGyro.text = "Orientation: y=${fmt(yaw)} p=${fmt(pitch)} r=${fmt(roll)}"
                }
                updateInferenceAndUi()

                val x = lastPosX
                val y = lastPosY
                if (x != null && y != null && ::labMap.isInitialized) {
                    labMap.setPose(x, y, lastYawDeg)
                }
            }
        }

        vm.start()

        viewLifecycleOwner.lifecycleScope.launch {
            vm.uiState.collect { state ->
                lastByTopic = state.lastByTopic

                if (::txtStatus.isInitialized) {
                    txtStatus.text = state.statusText
                }

                if (isFullUi && ::recycler.isInitialized) {
                    adapter.submit(state.lastByTopic)
                }

                val uwbMessage = state.lastByTopic["sf/UWB/uwb-a"]
                if (uwbMessage != null) {
                    val xy = tryParsePositionXY(uwbMessage.payload)
                    if (xy != null) {
                        lastPosX = xy.first
                        lastPosY = xy.second
                        if (::labMap.isInitialized) {
                            labMap.setPose(xy.first, xy.second, lastYawDeg)
                        }
                        updateInferenceAndUi()
                    }
                }

                updatePrinterTopicUi()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationTracker.start()
        if (::btnGyroReset.isInitialized) {
            btnGyroReset.requestFocus()
        }

        telemetryJob?.cancel()
        telemetryJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                vm.publishPoseLineProtocol(
                    topic = telemetryTopic,
                    deviceClass = deviceClass,
                    deviceId = deviceId,
                    tagName = tagName,
                    yaw = lastYawDeg,
                    pitch = lastPitchDeg,
                    roll = lastRollDeg,
                    posX = lastPosX,
                    posY = lastPosY,
                    lookAt = lastLookAtLabel,
                    lookAtDeltaDeg = lastLookAtDeltaDeg,
                    lookAtDistM = lastLookAtDist
                )
                delay(telemetryIntervalMs)
            }
        }
    }

    override fun onStop() {
        telemetryJob?.cancel()
        telemetryJob = null
        orientationTracker.stop()
        super.onStop()
    }

    private fun updateInferenceAndUi() {
        val x = lastPosX ?: return
        val y = lastPosY ?: return

        val yawWorld = -lastYawDeg +
            com.example.lookwhereyouwork.geometry.LabGeometry.yawOffsetDeg +
            com.example.lookwhereyouwork.geometry.LabGeometry.yawFlipDeg

        val res = inferPrinter(x, y, yawWorld)

        if (res == null) {
            if (::txtLookAt.isInitialized) txtLookAt.text = "Looking at: -"
            if (::txtLookDetails.isInitialized) txtLookDetails.text = "Delta: -"
            if (::labMap.isInitialized) labMap.setSelectedPrinter(null)
            lastLookAtLabel = null
            lastLookAtDeltaDeg = null
            lastLookAtDist = null
        } else {
            if (::txtLookAt.isInitialized) txtLookAt.text = "Looking at: ${res.label}"
            if (::txtLookDetails.isInitialized) txtLookDetails.text = "Delta: ${fmt(res.deltaDeg)}"
            if (::labMap.isInitialized) labMap.setSelectedPrinter(res.label)
            lastLookAtLabel = res.label
            lastLookAtDeltaDeg = res.deltaDeg
            lastLookAtDist = res.distance
            latchedPrinterLabel = res.label
        }

        if (::labMap.isInitialized) {
            labMap.setFovHalfDegrees(FOV_HALF_DEG)
            labMap.setPose(x, y, lastYawDeg)
        }
        updatePrinterTopicUi()
    }

    private fun bindMapPage(page: View) {
        txtStatus = page.findViewById(R.id.txtStatus)
        recycler = page.findViewById(R.id.recycler)
        debugPanel = page.findViewById(R.id.debugPanel)
        labMap = page.findViewById(R.id.labMap)
        txtGyro = page.findViewById(R.id.txtGyro)
        btnGyroReset = page.findViewById(R.id.btnGyroReset)
        txtLookAt = page.findViewById(R.id.txtLookAt)
        txtLookDetails = page.findViewById(R.id.txtLookDetails)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        btnGyroReset.isFocusable = true
        btnGyroReset.isFocusableInTouchMode = true
        btnGyroReset.requestFocus()
        btnGyroReset.setOnClickListener {
            orientationTracker.resetBaseline()
            btnGyroReset.requestFocus()
        }

        debugPanel.isVisible = isFullUi
        txtLookDetails.isVisible = isFullUi
        txtStatus.text = vm.uiState.value.statusText
        if (isFullUi) {
            adapter.submit(lastByTopic)
        }

        txtGyro.text = "Orientation: y=${fmt(lastYawDeg)} p=${fmt(lastPitchDeg)} r=${fmt(lastRollDeg)}"
        txtLookAt.text = "Looking at: ${lastLookAtLabel ?: "-"}"
        txtLookDetails.text = "Delta: ${lastLookAtDeltaDeg?.let(::fmt) ?: "-"}"
        labMap.setFovHalfDegrees(FOV_HALF_DEG)
        labMap.setSelectedPrinter(lastLookAtLabel)

        val x = lastPosX
        val y = lastPosY
        if (x != null && y != null) {
            labMap.setPose(x, y, lastYawDeg)
        }
    }

    private fun bindPrinterPage(page: View) {
        txtPrinterTopic = page.findViewById(R.id.txtPrinterTopic)
        txtPrinterPayload = page.findViewById(R.id.txtPrinterPayload)
        updatePrinterTopicUi()
    }

    private fun updatePrinterTopicUi() {
        val topicView = txtPrinterTopic ?: return
        val payloadView = txtPrinterPayload ?: return

        val topic = printerTopicForLabel(latchedPrinterLabel)
        if (topic == null) {
            topicView.text = "Topic: -"
            payloadView.text = "No printer selected yet."
            return
        }

        topicView.text = "Topic: $topic"
        val payload = lastByTopic[topic]?.payload ?: "<no message yet>"
        payloadView.text = payload
    }
}

private fun fmt(x: Float): String {
    val v = (x * 10f).roundToInt() / 10f
    return v.toString()
}

private fun tryParsePositionXY(payload: String): Pair<Float, Float>? {
    // Match only the raw UWB coordinates, not filteredPositionX/Y.
    val rxX = Regex("""(?:^|[,\s])positionX\s*=\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    val rxY = Regex("""(?:^|[,\s])positionY\s*=\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    val x = rxX.find(payload)?.groupValues?.get(1)?.toFloatOrNull()
    val y = rxY.find(payload)?.groupValues?.get(1)?.toFloatOrNull()

    return if (x != null && y != null) x to y else null
}

private const val FOV_HALF_DEG = 15f

private fun printerTopicForLabel(label: String?): String? =
    when (label) {
        "p_A" -> "sf/printer/a"
        "p_B" -> "sf/printer/b"
        "p_C" -> "sf/printer/c"
        "p_D" -> "sf/printer/d"
        else -> null
    }

private data class InferenceResult(
    val label: String,
    val deltaDeg: Float,
    val distance: Float
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

        val bearingRad = kotlin.math.atan2(dy, dx)
        val bearingDeg = bearingRad * 180f / Math.PI.toFloat()

        val delta = wrapTo180(bearingDeg - yawDegWorld)
        val absDelta = kotlin.math.abs(delta)
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

        if (absDelta <= FOV_HALF_DEG) {
            val cand = InferenceResult(label = p.label, deltaDeg = delta, distance = dist)
            best = when {
                best == null -> cand
                kotlin.math.abs(cand.deltaDeg) < kotlin.math.abs(best!!.deltaDeg) -> cand
                kotlin.math.abs(cand.deltaDeg) == kotlin.math.abs(best!!.deltaDeg) &&
                    cand.distance < best!!.distance -> cand
                else -> best
            }
        }
    }

    return best
}

private fun isPixelDevice(): Boolean {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val brand = android.os.Build.BRAND.lowercase()
    val model = android.os.Build.MODEL.lowercase()

    return (manufacturer.contains("google") || brand.contains("google")) &&
        model.contains("pixel")
}
