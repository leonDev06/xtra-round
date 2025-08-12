package com.example.wearosapp.device.model

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: Int,
    val caption: String,
    val message: String,
    val buttons: List<String>
)