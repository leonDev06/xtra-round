package com.example.wearosapp.device.model

import com.example.wearosapp.network.model.IotHubConfig
import kotlinx.serialization.Serializable

@Serializable
data class CachedIotHubConfig (
    val config: IotHubConfig,
    val savedOnMs: Long
)