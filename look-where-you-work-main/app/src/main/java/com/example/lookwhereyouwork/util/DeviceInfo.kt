package com.example.lookwhereyouwork.util

import android.os.Build
import java.util.Locale

object DeviceInfo {

    fun deviceClass(): String {
        val m = Build.MANUFACTURER.lowercase(Locale.US)
        val b = Build.BRAND.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)

        return when {
            (m.contains("google") || b.contains("google")) && model.contains("pixel") -> "pixel"
            (m.contains("google") || b.contains("google")) && model.contains("glass") -> "glass"
            m.contains("vuzix") || model.contains("vuzix") || model.contains("m400") || model.contains("blade") -> "vuzix"
            else -> "android"
        }
    }

    fun deviceId(): String {
        // stabil genug für MQTT topics (keine Spaces)
        val raw = "${Build.MANUFACTURER}-${Build.MODEL}"
        return raw.lowercase(Locale.US)
            .replace(Regex("""\s+"""), "-")
            .replace(Regex("""[^a-z0-9\-_]"""), "")
    }
}