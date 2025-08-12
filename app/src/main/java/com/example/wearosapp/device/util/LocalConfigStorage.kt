package com.example.wearosapp.device.util

import android.content.Context
import com.example.wearosapp.device.model.CachedIotHubConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

object LocalConfigStorage {
    private const val CONFIG_KEY = "cached_config"

    fun save(context: Context, config: CachedIotHubConfig) {
        val jsonConfig = Json.encodeToString(config)
        context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            .edit { putString(CONFIG_KEY, jsonConfig) }
    }

    fun load(context: Context): CachedIotHubConfig? {
        val json = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            .getString(CONFIG_KEY, null) ?: return null
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context) {
        context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            .edit { remove(CONFIG_KEY) }
    }

}