package com.example.lookwhereyouwork.ui.mqtt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lookwhereyouwork.model.MqttUiState
import com.example.lookwhereyouwork.mqtt.MqttConfig
import com.example.lookwhereyouwork.mqtt.MqttRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MqttViewModel(
    private val repo: MqttRepository = MqttRepository()
) : ViewModel() {

    val uiState = combine(
        repo.connected,
        repo.status,
        repo.lastByTopic
    ) { connected, status, lastByTopic ->

        // Ensure UI lists all configured topics even before the first message arrives
        val filled = MqttConfig.TOPICS.associateWith { topic -> lastByTopic[topic] }
            .filterValues { it != null }
            .mapValues { it.value!! }

        MqttUiState(
            connected = connected,
            statusText = status,
            lastByTopic = filled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MqttUiState())

    fun start() = repo.start()

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
}
