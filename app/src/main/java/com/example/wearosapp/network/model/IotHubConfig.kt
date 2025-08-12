package com.example.wearosapp.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IotHubConfig (

    @SerialName("iot_host")
    val iotHost: String,

    @SerialName("sas_token")
    val sasToken: String,

    @SerialName("expiry")
    val expiry: Float
)