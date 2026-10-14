
package com.example.backgroundservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class TrackerService : Service(), SensorEventListener {

    companion object {
        const val TAG = "TrackerService"
        const val CHANNEL_ID = "tracker_service_channel"
        const val JUMP_CHANNEL_ID = "jump_notification_channel"
        const val RUNNING_NOTIFICATION_ID = 1001
        const val JUMP_NOTIFICATION_ID = 1002
        const val JUMP_THRESHOLD = 15f
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isDetecting = false
    private var lastJumpTime = 0L

    override fun onCreate() {
        super.onCreate()

        sensorManager =
            getSystemService(SENSOR_SERVICE) as SensorManager

        accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createNotificationChannels()

        Log.d(TAG, "TrackerService dibuat")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val runningChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi saat service berjalan"
            }

            val jumpChannel = NotificationChannel(
                JUMP_CHANNEL_ID,
                "Deteksi Lompatan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi ketika lompatan terdeteksi"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(runningChannel)
            manager.createNotificationChannel(jumpChannel)
        }
    }

    private fun buildServiceRunningNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Background Service Aktif")
            .setContentText("Sedang mendeteksi gerakan melompat")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startJumpDetection() {
        if (isDetecting) return

        val sensor = accelerometer

        if (sensor == null) {
            Log.e(TAG, "Sensor akselerometer tidak tersedia")
            stopSelf()
            return
        }

        isDetecting = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        Log.d(TAG, "Deteksi lompatan dimulai: $isDetecting")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val notification = buildServiceRunningNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RUNNING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(
                RUNNING_NOTIFICATION_ID,
                notification
            )
        }

        Log.d(TAG, "Foreground service berjalan")

        startJumpDetection()

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null ||
            event.sensor.type != Sensor.TYPE_ACCELEROMETER
        ) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Menghitung besar percepatan dari tiga sumbu
        val acceleration = sqrt(
            x * x + y * y + z * z
        )

        Log.d(
            TAG,
            "X=$x, Y=$y, Z=$z, Acceleration=$acceleration"
        )

        val currentTime = System.currentTimeMillis()

        // Threshold sesuai modul: 15
        // Jeda 1,5 detik mencegah notifikasi berulang.
        if (acceleration > JUMP_THRESHOLD &&
            currentTime - lastJumpTime > 1500L
        ) {
            lastJumpTime = currentTime

            Log.d(TAG, "Gerakan melewati threshold terdeteksi")

            showJumpNotification()

            stopSelf()
        }
    }

    private fun showJumpNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("jump_detected", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            JUMP_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Gerakan Terdeteksi")
            .setContentText(
                "Lompatan terdeteksi. Background service dihentikan."
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(JUMP_NOTIFICATION_ID, notification)

        Log.d(TAG, "Notifikasi lompatan ditampilkan")
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // Tidak ada tindakan khusus saat akurasi berubah.
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        isDetecting = false

        stopForeground(STOP_FOREGROUND_REMOVE)

        Log.d(TAG, "TrackerService dihentikan")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
