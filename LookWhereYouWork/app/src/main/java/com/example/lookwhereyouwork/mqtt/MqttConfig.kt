package com.example.lookwhereyouwork.mqtt

object MqttConfig {
    const val HOST = "10.6.22.65"
    const val PORT = 1883

    // 1 bestehendes + 3 weitere Topics
    val TOPICS = listOf(
        "sf/UWB/uwb-a",
        "sf/printer/a",
        "sf/printer/b",
        "sf/printer/c",
        "sf/printer/d",
    )
}
