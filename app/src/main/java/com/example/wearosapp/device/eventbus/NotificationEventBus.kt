package com.example.wearosapp.device.eventbus

import com.example.wearosapp.device.model.Notification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotificationEventBus {
    private val _messages = MutableSharedFlow<Notification>(replay = 0, extraBufferCapacity = 5)
    val messages = _messages.asSharedFlow()

    suspend fun postFullMessage(message: Notification) {
        _messages.emit(message)
    }
}