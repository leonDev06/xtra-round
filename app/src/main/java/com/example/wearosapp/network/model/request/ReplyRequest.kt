package com.example.wearosapp.network.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ReplyRequest (
    val pressed: String
)