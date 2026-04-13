package com.example.lookwhereyouwork.model

data class MqttUiState(
    val connected: Boolean = false,
    val statusText: String = "MQTT: idle",
    val lastByTopic: Map<String, TopicMessage> = emptyMap()
)
