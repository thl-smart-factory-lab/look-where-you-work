package com.example.lookwhereyouwork.model

data class TopicMessage(
    val topic: String,
    val payload: String,
    val timestampMs: Long = System.currentTimeMillis()
)
