package com.example.wearosapp.device.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.wearosapp.device.model.DeviceInfoProvider
import com.example.wearosapp.network.model.MessageReplyResponse
import com.example.wearosapp.network.model.request.ReplyRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)

        // Dismiss from the notification tray
        val notificationId = intent.getIntExtra("notificationId", -1)
        val messageId = intent.getIntExtra("id", -1)
        if (notificationId != -1) {
            manager.cancel(notificationId)
        }

        val pressed = intent.getStringExtra("pressed") ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HttpClient(OkHttp) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

                val response = client.post("${DeviceInfoProvider.configApiUrl}/api/scratch/reply/$messageId"){
                    contentType(ContentType.Application.Json)
                    headers{
                        append("tenantID", "1")
                    }

                    setBody(ReplyRequest(pressed))
                }.body<MessageReplyResponse>()

                Log.i("REPLY RESPONSE", Json.encodeToString(response))
                true


            } catch (e: Exception) {
                e.printStackTrace() // swap with logging if needed
            }
        }
    }
}
