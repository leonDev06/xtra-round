package com.example.wearosapp.device.util

import android.util.Log
import com.example.wearosapp.device.model.DeviceInfoProvider
import com.example.wearosapp.network.model.IotHubConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ConfigManager {
    var config: IotHubConfig? = null

    suspend fun loadConfig(): Boolean {
        return try {
            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            // Fetch the config from the API
            config = client.post("${DeviceInfoProvider.configApiUrl}/api/watch/registration/${DeviceInfoProvider.androidId}"){
                contentType(ContentType.Application.Json)
                headers{
                    append("tenantID", "1")
                    append("apiKey", "xtra")
                }
            }.body()
            true

        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to fetch config: ${e.message}")
            false
        }
    }
}