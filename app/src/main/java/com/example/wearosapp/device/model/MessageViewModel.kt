package com.example.wearosapp.device.model

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wearosapp.device.eventbus.NotificationEventBus
import kotlinx.coroutines.launch


class MessageViewModel : ViewModel() {

    val messages = mutableStateListOf<Notification>()

    init {
        viewModelScope.launch {
            NotificationEventBus.messages.collect { newMessage ->
                addRawMessage(newMessage)
            }
        }
    }

    private fun addRawMessage(message: Notification) {
        messages.add(0, message)
    }
}