/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.wearosapp.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Text
import com.example.wearosapp.device.model.DeviceInfoProvider
import com.example.wearosapp.device.model.Notification
import com.example.wearosapp.device.util.ConfigManager
import com.example.wearosapp.device.util.LocalConfigStorage
import com.example.wearosapp.device.util.NotificationManager
import com.example.wearosapp.network.model.IotHubConfig
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttWebSocketConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    // IoTHubConfiguration
    val config: IotHubConfig? = ConfigManager.config

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS" // Safe fallback
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
            }
        }

        setContent {
            val config = remember { LocalConfigStorage.load(baseContext) }
            var isConnected by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(config) {
                if (config != null) {
                    connect { result ->
                        isConnected = result
                        if (!result) {
                            LocalConfigStorage.delete(baseContext)
                        }
                    }
                }
            }

            if (config == null || isConnected == false) {
                UnrecognizedDeviceScreen()
            }
        }


    }

    fun connect(onResult: (Boolean) -> Unit) {
        val mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(DeviceInfoProvider.androidId)
            .serverHost(config!!.iotHost)
            .serverPort(443)
            .webSocketConfig(
                MqttWebSocketConfig.builder()
                    .serverPath("/mqtt")
                    .build()
            )
            .sslWithDefaultConfig()
            .buildAsync()

        mqttClient.connectWith()
            .simpleAuth()
            .username("${config.iotHost}/${DeviceInfoProvider.androidId}/?api-version=2021-04-12")
            .password(config.sasToken.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { connAck, throwable ->
                if (throwable != null) {
                    onResult(false)
                } else {
                    Log.i("CONN", "Connected successfully")
                    subscribeToMessages(mqttClient, baseContext)
                    onResult(true)
                }
            }
    }


    fun subscribeToMessages(
        client: Mqtt3AsyncClient,
        context: Context
    ) {
        val notifier = NotificationManager(context)

        client.subscribeWith()
            .topicFilter("devices/${DeviceInfoProvider.androidId}/messages/devicebound/#")
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->

                // Receive the message
                val message = publish.payload?.get()?.let {
                    StandardCharsets.UTF_8.decode(it).toString()
                }

                if (message == null)
                    return@callback

                // Transform the message to JSON
                val notification = Json.decodeFromString<Notification>(message)


                val permissionCheck = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                )

                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    Log.w("MQTT", "Notification permission not granted")
                    return@callback
                }

                notifier.showNotification(notification)
            }
            .send()
    }

    @Composable
    fun UnrecognizedDeviceScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Unrecognized Device. ${DeviceInfoProvider.androidId}",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}