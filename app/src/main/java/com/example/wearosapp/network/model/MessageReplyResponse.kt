package com.example.wearosapp.network.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageReplyResponse (
    val pressed: String
)