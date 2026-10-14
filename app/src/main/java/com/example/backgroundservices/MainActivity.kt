package com.example.backgroundservices

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var jumpCountTextView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStartWorkout: Button
    private lateinit var btnCalendar: Button

    private var finalJumpCount = 0
    private var workoutStartTime = 0L
    private var workoutEndTime = 0L

    companion object {
        const val SERVICE_ID =
            "com.example.backgroundservices.TRACKER"

        const val EXTRA_JUMP_DETECTED = "jump_detected"
        const val EXTRA_JUMP_COUNT = "jump_count"
        const val EXTRA_START_TIME = "workout_start_time"
        const val EXTRA_END_TIME = "workout_end_time"
    }

    // Meminta izin yang diperlukan
    private val permissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val denied = permissions.filterValues { !it }.keys

            if (denied.isEmpty() && hasAllPermissions()) {
                startTrackerService(closeActivity = true)
            } else {
                statusTextView.text =
                    "Izin ditolak. Service tidak dapat dijalankan."

                btnStartWorkout.isEnabled = true
                btnStart.isEnabled = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        jumpCountTextView = findViewById(R.id.jumpCountTextView)
        btnStart = findViewById(R.id.btnStart)
        btnStartWorkout = findViewById(R.id.btnStartWorkout)
        btnCalendar = findViewById(R.id.btnCalendar)

        btnCalendar.visibility = View.GONE

        // Menampilkan hasil jika aplikasi dibuka dari notifikasi
        handleIntent(intent)

        // Tombol utama untuk memulai latihan
        btnStartWorkout.setOnClickListener {
            startWorkout()
        }

        // Tombol pelacakan aktivitas tetap dipertahankan
        btnStart.setOnClickListener {
            startWorkout()
        }

        // Membuka formulir event kalender
        btnCalendar.setOnClickListener {
            createCalendarEvent()
        }
    }

    private fun startWorkout() {
        if (hasAllPermissions()) {
            startTrackerService(closeActivity = true)
        } else {
            requestRequiredPermissions()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        // Izin aktivitas fisik diperlukan mulai Android 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Izin notifikasi diperlukan mulai Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isEmpty()) {
            startTrackerService(closeActivity = true)
        } else {
            permissionRequest.launch(permissions.toTypedArray())
        }
    }

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

    private fun startTrackerService(closeActivity: Boolean = false) {
        val serviceIntent = Intent(
            this,
            TrackerService::class.java
        ).apply {
            action = SERVICE_ID
        }

        // Memulai foreground service
        ContextCompat.startForegroundService(
            this,
            serviceIntent
        )

        statusTextView.text = "Background Service sedang berjalan..."
        btnStart.text = "Service Berjalan"
        btnStartWorkout.text = "Latihan Sedang Berjalan"
        btnStartWorkout.isEnabled = false
        btnStart.isEnabled = false

        // Activity ditutup agar service berjalan di background
        if (closeActivity) {
            finish()
        }
    }

    // Menerima hasil latihan dari notifikasi
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_JUMP_DETECTED, false) != true) {
            return
        }

        finalJumpCount = intent.getIntExtra(EXTRA_JUMP_COUNT, 0)

        workoutStartTime = intent.getLongExtra(
            EXTRA_START_TIME,
            0L
        )

        workoutEndTime = intent.getLongExtra(
            EXTRA_END_TIME,
            0L
        )

        statusTextView.text = "Latihan selesai!"

        jumpCountTextView.text =
            "Jumlah lompatan: $finalJumpCount"

        btnStart.text = "Mulai Lacak Aktivitas"
        btnStartWorkout.text = "Mulai Latihan Lagi"

        btnStart.isEnabled = true
        btnStartWorkout.isEnabled = true

        btnCalendar.visibility = View.VISIBLE

        // Mencegah hasil lama diproses kembali saat Activity dibuat ulang
        setIntent(Intent(this, MainActivity::class.java))
    }

    // Membuka formulir untuk menyimpan event kalender
    private fun createCalendarEvent() {
        if (workoutStartTime <= 0L ||
            workoutEndTime <= workoutStartTime
        ) {
            statusTextView.text =
                "Waktu latihan tidak tersedia. Silakan ulangi latihan."
            return
        }

        val calendarIntent = Intent(
            Intent.ACTION_INSERT
        ).apply {
            data = CalendarContract.Events.CONTENT_URI

            putExtra(
                CalendarContract.Events.TITLE,
                "Latihan Fisik"
            )

            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Latihan selesai dengan jumlah " +
                        "$finalJumpCount gerakan."
            )

            putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                workoutStartTime
            )

            putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                workoutEndTime
            )
        }

        if (calendarIntent.resolveActivity(packageManager) != null) {
            startActivity(calendarIntent)
        } else {
            statusTextView.text =
                "Aplikasi kalender tidak ditemukan."
        }
    }

    // Dipanggil ketika Activity yang sama menerima intent baru
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}