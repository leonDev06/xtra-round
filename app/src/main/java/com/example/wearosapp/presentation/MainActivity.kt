/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.wearosapp.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.example.wearosapp.device.foreground.MqttForegroundService
import com.example.wearosapp.device.model.DeviceInfoProvider
import com.example.wearosapp.device.model.MessageViewModel
import com.example.wearosapp.device.model.Notification
import com.example.wearosapp.device.util.LocalConfigStorage
import com.example.wearosapp.presentation.theme.WearOsAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var isMqttConnected: MutableState<Boolean>


    // BroadcastReceiver to listen for connection status changes
    private val connectionStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MqttForegroundService.ACTION_MQTT_CONNECTION_STATUS) {
                val connected = intent.getBooleanExtra(MqttForegroundService.EXTRA_MQTT_IS_CONNECTED, false)
                Log.d("MainActivity", "Received MQTT connection status: $connected")
                if (::isMqttConnected.isInitialized) {
                    isMqttConnected.value = connected
                }
                // If not connected, the UI will update based on isMqttConnected.value
            }
        }
    }

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

        MqttForegroundService.start(this)

        setContent {
            // Load initial config
            val initialConfig = remember { LocalConfigStorage.load(baseContext) }
            val messageViewModel: MessageViewModel = viewModel()

            isMqttConnected = remember { mutableStateOf(initialConfig != null) }

            WearOsAppTheme {
                // If there's no initial config OR if MQTT is not connected, show UnrecognizedDeviceScreen
                if (initialConfig == null || !isMqttConnected.value) {
                    UnrecognizedDeviceScreen()
                } else {
                    MessageScreen(messageViewModel = messageViewModel)
                }
            }
        }
    }

    fun isFirstTimeInitialization(): Boolean{
        val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val serviceHasBeenInitiatedByApp = prefs.getBoolean("on_first_time_init", false)

        return serviceHasBeenInitiatedByApp
    }

    // Simple Composable Screen to host the list
    @Composable
    fun MessageScreen(messageViewModel: MessageViewModel) {
        // Collect the messages. Compose will automatically recompose when this list changes.
        val messages = messageViewModel.messages

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
        ) {
            MessageList(messages = messages)
        }
    }

    @Composable
    fun MessageList(messages: List<Notification>, modifier: Modifier = Modifier) {
        if (messages.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages yet.", textAlign = TextAlign.Center)
            }
            return
        }

        val listState = rememberLazyListState()

         LaunchedEffect(messages.firstOrNull()) {
             messages.firstOrNull()?.let {
                 listState.animateScrollToItem(0)
             }
         }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { message -> // Use key for better performance
                MessageRow(message = message)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    @Composable
    fun MessageRow(
        message: Notification,
        modifier: Modifier = Modifier,
        onButtonClicked: (buttonAction: String) -> Unit = {} // Callback for button clicks
    ) {
        Card(
            onClick = { /* Handle click on the whole card if needed, e.g., expand details */ },
            modifier = modifier.fillMaxWidth()
            // Consider adding elevation or specific colors if desired
            // colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) { // Increased padding for better spacing

                // 1. Caption
                if (message.caption.isNotBlank()) {
                    Text(
                        text = message.caption,
                        style = MaterialTheme.typography.titleSmall, // Or titleMedium
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface // Use theme colors
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 2. Message
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium, // Or bodySmall
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Use theme colors
                )

                // 3. Buttons (if any)
                if (message.buttons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.buttons.size == 1) Arrangement.Center else Arrangement.SpaceEvenly, // Or Arrangement.End
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        message.buttons.take(3).forEach { buttonInfo -> // Display up to 3 buttons for example
                            // Using Wear OS specific Button
                            androidx.wear.compose.material.Button(
                                // Note: Using Wear Button
                                onClick = {  },
                                // Optional: Adjust button size for Wear OS
                                 modifier = Modifier
                                     .weight(1f)
                                     .padding(horizontal = 2.dp) // If you want them to take equal space
                                // colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) // M3 ButtonDefaults
                            ) {
                                Text(
                                    text = buttonInfo.uppercase(), // Uppercase for button text is common
                                    style = MaterialTheme.typography.labelSmall // Use a smaller style for button text on Wear
                                )
                            }
                        }
                    }
                }
            }
        }
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