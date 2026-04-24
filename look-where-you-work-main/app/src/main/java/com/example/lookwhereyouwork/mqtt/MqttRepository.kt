package com.example.lookwhereyouwork.mqtt


import android.util.Log
import com.example.lookwhereyouwork.model.TopicMessage
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class MqttRepository {

    private val tag = "MQTT"
    private val started = AtomicBoolean(false)

    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .identifier("android-" + UUID.randomUUID().toString())
        .serverHost(MqttConfig.HOST)
        .serverPort(MqttConfig.PORT)
        .buildAsync()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _status = MutableStateFlow("MQTT: idle")
    val status: StateFlow<String> = _status

    private val _lastByTopic = MutableStateFlow<Map<String, TopicMessage>>(emptyMap())
    val lastByTopic: StateFlow<Map<String, TopicMessage>> = _lastByTopic

    fun start() {
        if (!started.compareAndSet(false, true)) return
        connectAndSubscribe(MqttConfig.TOPICS)
    }

    fun stop() {
        started.set(false)
        try {
            client.disconnect()
        } catch (_: Exception) { }
        _connected.value = false
        _status.value = "MQTT: disconnected"
    }

    private fun connectAndSubscribe(topics: List<String>) {
        postStatus("MQTT: connect ${MqttConfig.HOST}:${MqttConfig.PORT} ...")

        client.connectWith()
            .cleanSession(true)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    _connected.value = false
                    postStatus("MQTT: connect failed: ${throwable.message}")
                    Log.e(tag, "Connect failed", throwable)
                    return@whenComplete
                }

                _connected.value = true
                postStatus("MQTT: connected. Subscribing ${topics.size} topics ...")

                topics.forEach { topic ->
                    subscribe(topic)
                }
            }
    }

    private fun subscribe(topic: String) {
        client.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                val t = publish.topic.toString()
                val payload = try {
                    String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    "<decode error: ${e.message}>"
                }

                _lastByTopic.update { old ->
                    old + (t to TopicMessage(topic = t, payload = payload))
                }
            }
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(tag, "Subscribe failed for $topic", throwable)
                    postStatus("MQTT: subscribe failed for $topic: ${throwable.message}")
                } else {
                    postStatus("MQTT: subscribed $topic")
                }
            }
    }

    private fun postStatus(text: String) {
        _status.value = text
        Log.d(tag, text)
    }


    fun publishPoseLineProtocol(
        topic: String,
        deviceClass: String,
        deviceId: String,
        tagName: String,
        yawDeg: Float,
        pitchDeg: Float,
        rollDeg: Float,
        posX: Float?,
        posY: Float?,
        lookAt: String?,
        lookAtDeltaDeg: Float?,
        lookAtDistM: Float?
    ) {
        if (!_connected.value) return

        val tsNs = System.currentTimeMillis() * 1_000_000L

        // Sentinel-Werte (niemals NaN!)
        val POS_INVALID = -9999.0f
        val ANG_INVALID =  9999.0f
        val DIST_INVALID = 9999.0f

        fun safe(v: Float, invalid: Float): Float = if (v.isFinite()) v else invalid
        fun safeOpt(v: Float?, invalid: Float): Float = if (v != null && v.isFinite()) v else invalid

        val x = safeOpt(posX, POS_INVALID)
        val y = safeOpt(posY, POS_INVALID)

        val yaw = safe(yawDeg, ANG_INVALID)
        val pitch = safe(pitchDeg, ANG_INVALID)
        val roll = safe(rollDeg, ANG_INVALID)

        val delta = safeOpt(lookAtDeltaDeg, ANG_INVALID)
        val dist = safeOpt(lookAtDistM, DIST_INVALID)

        val look = if (!lookAt.isNullOrBlank()) lookAt else "none"

        // Konstant: immer dieselben Fields, gleiche Reihenfolge
        val payload =
            "pose,deviceClass=${escapeInfluxTag(deviceClass)},deviceId=${escapeInfluxTag(deviceId)},tagName=${escapeInfluxTag(tagName)} " +
                    "yaw=${fmt(yaw)},pitch=${fmt(pitch)},roll=${fmt(roll)}," +
                    "positionX=${fmt(x)},positionY=${fmt(y)}," +
                    "lookAt=\"${escapeInfluxStringField(look)}\",lookAtDeltaDeg=${fmt(delta)},lookAtDistM=${fmt(dist)}" +
                    " " + tsNs

        client.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_MOST_ONCE)
            .payload(payload.toByteArray(Charsets.UTF_8))
            .send()
    }

    private fun escapeInfluxTag(s: String): String {
        // Tag escaping: space, comma, equals
        return s.replace("\\", "\\\\")
            .replace(" ", "\\ ")
            .replace(",", "\\,")
            .replace("=", "\\=")
    }

    private fun escapeInfluxStringField(s: String): String {
        // String field escaping: backslash and double quote
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun fmt(v: Float): String {
        // Influx will Punkt als Dezimaltrenner -> Locale.US erzwingen
        return java.lang.String.format(java.util.Locale.US, "%.2f", v)
    }
}


