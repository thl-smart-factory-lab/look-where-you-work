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
}
