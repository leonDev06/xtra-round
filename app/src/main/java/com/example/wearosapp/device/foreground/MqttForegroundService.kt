package com.example.wearosapp.device.foreground

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wearosapp.R
import com.example.wearosapp.device.eventbus.NotificationEventBus
import com.example.wearosapp.device.model.CachedIotHubConfig
import com.example.wearosapp.device.model.DeviceInfoProvider
import com.example.wearosapp.device.model.Notification
import com.example.wearosapp.device.util.ConfigManager
import com.example.wearosapp.device.util.LocalConfigStorage
import com.example.wearosapp.device.util.NotificationManager
import com.example.wearosapp.network.model.IotHubConfig
import com.example.wearosapp.presentation.MainActivity
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import android.app.NotificationManager as AndroidNotificationManager

class MqttForegroundService : Service() {

    private val TAG = "MqttForegroundService"
    private val NOTIFICATION_CHANNEL_ID = "MqttServiceChannel"
    private val NOTIFICATION_ID = 1
    private val RECONNECT_DELAY_MS = 5000L

    private var mqttClient: Mqtt3AsyncClient? = null
    private var iotHubConfig: IotHubConfig? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var customNotificationManager: NotificationManager

    @Volatile
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate() - Performing one-time setup.")

        // Acquire a wake lock
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearOSApp::MqttWakeLock")
        wakeLock.acquire(30 * 60 * 1000)

        customNotificationManager = NotificationManager(this)
        createNotificationChannel()

        // Initial notification before config loading
        startForeground(NOTIFICATION_ID, createNotification("Service Initializing..."))
    }

    private fun isTokenValid(cachedConfig: CachedIotHubConfig): Boolean {
        val now = System.currentTimeMillis()
        val savedTokenOn = cachedConfig.savedOnMs
        // Ensure expiry is a positive value to avoid issues if it's 0 or negative
        return (now - savedTokenOn) < cachedConfig.config.expiry && cachedConfig.config.expiry > 0
    }

    @SuppressLint("CheckResult")
    private fun connectMqtt() {
        // Ensure IoT Hub config is present
        if (iotHubConfig == null) {
            Log.e(TAG, "Cannot connect, IoT Hub config is null.")
            updateNotification("Connection Failed: No Config")

            // TODO: Load config if missing
            scheduleReconnect()
            return
        }

        // Ensure not already connected or trying to reconnect
        if (mqttClient?.state?.isConnectedOrReconnect == true) {
            Log.i(TAG, "MQTT: Already connected or connecting.")
            return
        }

        Log.i(TAG, "MQTT: Attempting to connect...")
        updateNotification("Connecting to Broker...")

        // Ensure DeviceId was fetched properly
        if (DeviceInfoProvider.androidId.isBlank() || DeviceInfoProvider.androidId == "UNKNOWN") {
            Log.e(TAG, "Android ID is not available. Cannot connect MQTT.")
            updateNotification("Error: Device ID Missing")
            stopSelf()
            return
        }

        // Begin building the Mqtt Client
        val clientBuilder = MqttClient.builder()
            .useMqttVersion3()
            // Auth
            .simpleAuth()
            .username("${iotHubConfig!!.iotHost}/${DeviceInfoProvider.androidId}/?api-version=2021-04-12")
            .password(iotHubConfig!!.sasToken.toByteArray())
            .applySimpleAuth()

            // Server
            .identifier(DeviceInfoProvider.androidId)
            .serverHost(iotHubConfig!!.iotHost)
            .serverPort(8883)
            .sslWithDefaultConfig() // Uses default SSL context

        clientBuilder.addDisconnectedListener { context ->
            Log.w(
                TAG,
                "MQTT client disconnected by library. Source: ${context.source}, Cause: ${context.cause}"
            )

            if (serviceScope.isActive) {

                Log.i(TAG, "Disconnected listener triggered: Scheduling a reconnect.")
                // Ensure updateNotification can be called from this thread
                // If it needs main thread, dispatch it:
                // CoroutineScope(Dispatchers.Main).launch { updateNotification("Disconnected. Reconnecting...") }
                updateNotification("Disconnected. Reconnecting...") // Assuming this is safe or handles its thread
                scheduleReconnect() // Trigger your reconnect logic
            } else {
                Log.w(
                    TAG,
                    "Disconnected listener triggered, but service scope is not active. No reconnect scheduled."
                )
            }
        }

        mqttClient = clientBuilder.buildAsync()

        // The rest of your connectWith() logic
        mqttClient
            ?.connectWith()
            ?.keepAlive(30)
            ?.cleanSession(false)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    // Schedule a retry if initial connect attempt fails
                    Log.e(TAG, "MQTT Connection failed: ${throwable.message}", throwable)
                    updateNotification("Connection Failed. Retrying...")
                    scheduleReconnect()
                } else {
                    Log.i(TAG, "MQTT Connected successfully")
                    updateNotification("Connected")

                    // Upon successful MQTT connection, subscribe to messages
                    subscribeToMessages()
                    sendConnectionStatusBroadcast(true)
                }
            }
    }

    private fun subscribeToMessages() {
        if (mqttClient == null || iotHubConfig == null) {
            Log.w(TAG, "MQTT client or config is null, cannot subscribe.")
            return
        }

        val topic = "devices/${DeviceInfoProvider.androidId}/messages/devicebound/#"
        Log.i(TAG, "MQTT: Subscribing to topic: $topic")

        mqttClient?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                val message = publish.payload?.get()?.let {
                    StandardCharsets.UTF_8.decode(it).toString()
                }
                Log.i(TAG, "MQTT Message received: $message")

                if (message.isNullOrEmpty()) {
                    Log.w(TAG, "Received empty or null message.")
                    return@callback
                }

                // 1. Push notifications
                try {
                    val notificationData = Json.decodeFromString<Notification>(message)
                    customNotificationManager.showNotification(notificationData)

                    // 2. Broadcast message for UI update
                    serviceScope.launch {
                        try {
                            NotificationEventBus.postFullMessage(notificationData)
                            Log.d(TAG, "Posted new message to EventBus: $message")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error posting message to EventBus", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing message or showing notification", e)
                }
            }
            ?.send()
            ?.whenComplete { ack, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "MQTT Subscription failed: ${throwable.message}", throwable)
                    updateNotification("Subscription Failed. Retrying...")
                    scheduleReconnect()
                } else {
                    Log.i(TAG, "MQTT: Successfully subscribed to topic")
                }
            }
    }

    private fun scheduleReconnect() {
        if (!serviceScope.isActive) {
            Log.w(TAG, "Service scope not active, cannot schedule reconnect.")
            return
        }

        // Add a check to prevent multiple reconnect schedules if one is already pending
        serviceScope.launch {
            Log.i(TAG, "Scheduling reconnect in ${RECONNECT_DELAY_MS / 1000} seconds")
            updateNotification("Disconnected. Reconnecting soon...")
            delay(RECONNECT_DELAY_MS)
            if (isActive) { // Check if still active and should proceed
                Log.i(TAG, "Attempting to reconnect...")
                disconnectMqtt(false) // Disconnect silently before reconnect
                connectMqtt()
            }
        }
    }

    private fun disconnectMqtt(broadcastStatus: Boolean = true) {
        val isConnected = mqttClient?.state?.isConnected ?: false
        if (!isConnected) {
            Log.w(TAG, "MQTT client is not connected, cannot disconnect.")
            return
        }

        mqttClient?.disconnect()?.whenComplete { _, throwable ->
            if (throwable != null) {
                Log.e(TAG, "Error disconnecting MQTT client: ${throwable.message}", throwable)
            } else {
                Log.i(TAG, "MQTT client disconnected.")
            }

            LocalConfigStorage.delete(this@MqttForegroundService)

            if (broadcastStatus) {
                sendConnectionStatusBroadcast(false)
                updateNotification("Disconnected")
            }
        }
         // Nullify client to ensure fresh object on reconnect
         mqttClient = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand - Received start request. Initialized: $isInitialized")

        runBlocking {
            if (!isInitialized) {
                Log.i(TAG, "Service is not yet initialized or config needs loading. Proceeding with setup.")
                // Load configuration
                val configLoadedSuccessfully = loadConfigurationSynchronouslyOrAsync()
                if (configLoadedSuccessfully && iotHubConfig != null) {
                    isInitialized = true // Mark as initialized after successful config load
                    Log.i(TAG, "Configuration loaded. Attempting MQTT connection.")
                    updateNotification("Connecting...")
                    connectMqtt()
                } else {
                    Log.e(TAG, "IoT Hub configuration not available after attempt. Stopping service.")

                    LocalConfigStorage.delete(this@MqttForegroundService)

                    updateNotification("Config Error. Service Stopped.")
                    stopSelf()
                }
            } else {
                Log.i(TAG, "Service already initialized. Ensuring MQTT is connected.")

                if (iotHubConfig != null && (mqttClient?.state?.isConnected != true)) {
                    Log.w(TAG, "Service was initialized, but MQTT not connected. Attempting reconnect.")
                    updateNotification("Reconnecting...")
                    connectMqtt()
                } else if (iotHubConfig == null) {
                    Log.e(TAG,"Service was initialized, but config became null somehow. Attempting reload/stop.")
                    isInitialized = false // Reset, so it tries to load config again on next start

                    stopSelf()
                } else {
                    Log.i(TAG, "Service initialized and MQTT likely connected or connecting.")

                }
            }
        }

        return START_STICKY
    }

    // Example of your config loading, extracted for clarity
    // Make this return a boolean indicating success
    private suspend fun loadConfigurationSynchronouslyOrAsync(): Boolean {

        Log.d(TAG, "Loading IoT Hub configuration...")
        val cached = LocalConfigStorage.load(this@MqttForegroundService)

        if (cached != null && isTokenValid(cached)) {
            this.iotHubConfig = cached.config
            Log.i(TAG, "Using cached and valid config.")
        } else {
            if (cached != null) Log.i(TAG, "Cached config found but token is expired. Fetching new config.")
            else Log.i(TAG, "No cached config found. Fetching new config.")

            updateNotification("Fetching Configuration...")
            val success = ConfigManager.loadConfig()
            if (success && ConfigManager.config != null) {
                this.iotHubConfig = ConfigManager.config
                Log.i(TAG, "Successfully fetched new config.")
                val newCache = CachedIotHubConfig(config = this.iotHubConfig!!, savedOnMs = System.currentTimeMillis())
                LocalConfigStorage.save(this@MqttForegroundService, newCache)
            } else {
                Log.e(TAG, "Failed to fetch new config.")
                this.iotHubConfig = null
            }
        }
        return this.iotHubConfig != null
    }

    override fun onDestroy() {
        super.onDestroy()

        isInitialized = false

        Log.i(TAG, "Service Destroying...")
        serviceJob.cancel() // Cancel all coroutines within serviceScope
        disconnectMqtt() // Ensure MQTT client is disconnected
        Log.i(TAG, "Service Destroyed")

        stop(baseContext)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not using binding
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MQTT Connectivity Service",
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles background MQTT communication"
            }
            val manager = getSystemService(AndroidNotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Opens MainActivity on tap
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Device Sync Service")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes it non-dismissable
            .setPriority(NotificationCompat.PRIORITY_LOW) // Matches channel importance
            .build()
    }

    private fun updateNotification(contentText: String) {
        // Debounce or limit frequent updates if text changes very rapidly
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification updated: $contentText")
    }

    private fun sendConnectionStatusBroadcast(isConnected: Boolean) {
        val intent = Intent(ACTION_MQTT_CONNECTION_STATUS).apply {
            putExtra(EXTRA_MQTT_IS_CONNECTED, isConnected)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Sent connection status broadcast: isConnected = $isConnected")
    }

    companion object {
        const val ACTION_MQTT_CONNECTION_STATUS = "com.example.wearosapp.MQTT_CONNECTION_STATUS"
        const val EXTRA_MQTT_IS_CONNECTED = "com.example.wearosapp.MQTT_IS_CONNECTED"

        const val ACTION_MQTT_MESSAGE_RECEIVED = "com.example.wearosapp.MQTT_MESSAGE_RECEIVED"
        const val EXTRA_MQTT_MESSAGE_CONTENT = "com.example.wearosapp.MQTT_MESSAGE_CONTENT"

        fun start(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent) // For pre-Oreo, startForeground needs to be called from service's onCreate/onStartCommand
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java)
            context.stopService(intent)
        }
    }
}