package com.example.wearosapp.device.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.wearosapp.device.foreground.MqttForegroundService

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device has finished booting.")

            // Starts the MQTT foreground service
            // MqttForegroundService.start(context)
        }
    }
}