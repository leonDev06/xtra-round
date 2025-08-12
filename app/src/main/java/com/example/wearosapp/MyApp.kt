package com.example.wearosapp

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import android.util.Log
import com.example.wearosapp.device.model.CachedIotHubConfig
import com.example.wearosapp.device.model.DeviceInfoProvider
import com.example.wearosapp.device.util.ConfigManager
import com.example.wearosapp.device.util.LocalConfigStorage
import kotlinx.coroutines.runBlocking

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Get the AndroidId
        fetchAndroidId()

        // Load the App Config
        runBlocking {
            // Try to fetch the config from the cache
            val cached = LocalConfigStorage.load(this@MyApp)

            // Cached config found and is valid token
            if (cached != null && isTokenValid(cached)) {
                ConfigManager.config = cached.config
                Log.d("ConfigLoader", "Using cached config")

                return@runBlocking
            }

            // Cached config found but token is expired OR cached config not found
            // Fetch config from the API
            if ((cached != null && !isTokenValid(cached)) ||
                cached == null
            ) {
                val success = ConfigManager.loadConfig()
                if (success) {
                    // Cache the fetched config
                    val newCache = CachedIotHubConfig(
                        config = ConfigManager.config!!,
                        savedOnMs = System.currentTimeMillis()
                    )
                    LocalConfigStorage.save(this@MyApp, newCache)
                }

                return@runBlocking
            }
        }

    }

    private fun isTokenValid(cachedConfig: CachedIotHubConfig): Boolean {
        val now = System.currentTimeMillis()
        val savedTokenOn = cachedConfig.savedOnMs

        // Elapsed time is greater
        return (now - savedTokenOn) < cachedConfig.config.expiry
    }

    @SuppressLint("HardwareIds")
    private fun fetchAndroidId() {
        DeviceInfoProvider.androidId = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

}