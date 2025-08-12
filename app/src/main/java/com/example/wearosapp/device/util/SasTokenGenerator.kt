package com.example.wearosapp.device.util

import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SasTokenGenerator {

    fun generateSasToken(
        hostName: String,
        deviceId: String,
        key: String,
        expirySeconds: Long = 3600
    ): String {
        val resourceUri = "$hostName/devices/$deviceId"
        val expiryTime = (System.currentTimeMillis() / 1000) + expirySeconds
        val encodedUri = URLEncoder.encode(resourceUri, "UTF-8")

        val toSign = "$encodedUri\n$expiryTime"

        val signature = try {
            val decodedKey = Base64.getDecoder().decode(key)
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(decodedKey, "HmacSHA256")
            mac.init(secretKeySpec)
            val rawHmac = mac.doFinal(toSign.toByteArray())
            URLEncoder.encode(Base64.getEncoder().encodeToString(rawHmac), "UTF-8")
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate HMAC signature", e)
        }

        return "SharedAccessSignature sr=$encodedUri&sig=$signature&se=$expiryTime"
    }
}