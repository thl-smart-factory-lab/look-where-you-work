package com.example.lookwhereyouwork.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.lookwhereyouwork.R
import android.view.WindowManager

class MainActivity : AppCompatActivity() {

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "lookwhereyouwork:wakelock")
        wakeLock?.acquire()
    }

    override fun onPause() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onPause()
    }
}
