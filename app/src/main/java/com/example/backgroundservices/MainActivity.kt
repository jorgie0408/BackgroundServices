package com.example.backgroundservices

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button

    companion object {
        const val SERVICE_ID =
            "com.example.backgroundservices.TRACKER"
    }

    // Meminta permission yang diperlukan
    private val permissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val denied = permissions.filterValues {
                !it
            }.keys

            if (denied.isEmpty() && hasAllPermissions()) {
                startTrackerService(closeActivity = true)
            } else {
                statusTextView.text =
                    "Izin ditolak. Service tidak dapat dijalankan."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        btnStart = findViewById(R.id.btnStart)

        // Menangani intent dari notifikasi
        handleIntent(intent)

        // Tombol untuk memulai background service
        btnStart.setOnClickListener {
            if (hasAllPermissions()) {
                startTrackerService(closeActivity = true)
            } else {
                requestRequiredPermissions()
            }
        }
    }

    // Meminta permission kepada pengguna
    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        if (permissions.isEmpty()) {
            startTrackerService(closeActivity = true)
        } else {
            permissionRequest.launch(
                permissions.toTypedArray()
            )
        }
    }

    // Memeriksa apakah semua permission telah diberikan
    private fun hasAllPermissions(): Boolean {
        val activityPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED

        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        return activityPermissionGranted &&
                notificationPermissionGranted
    }

    // Menjalankan TrackerService
    private fun startTrackerService(
        closeActivity: Boolean = false
    ) {
        val serviceIntent = Intent(
            this,
            TrackerService::class.java
        ).apply {
            action = SERVICE_ID
        }

        ContextCompat.startForegroundService(
            this,
            serviceIntent
        )

        statusTextView.text =
            "Background Service sedang berjalan..."

        btnStart.text = "Service Berjalan"

        // Menutup Activity setelah service dimulai
        if (closeActivity) {
            finish()
        }
    }

    // Menangani intent dari notifikasi ketika lompatan terdeteksi
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                "jump_detected",
                false
            ) == true
        ) {
            statusTextView.text =
                "Lompatan terdeteksi. Service telah berhenti."

            btnStart.text = "Start Service Lagi"
        }
    }

    // Menangani intent baru ketika Activity sudah aktif
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}