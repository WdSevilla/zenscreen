package com.freekiosk.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.text.format.Formatter

class ServerService : Service() {

    companion object {
        const val SERVER_PORT = 8080
        private const val CHANNEL_ID = "freekiosk_server"
        private const val NOTIF_ID = 1
    }

    private var server: WifiServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Solo arrancar si no está corriendo ya (evita EADDRINUSE en reintentos)
        if (server == null || !server!!.isAlive) {
            val pdfsDir = FileManager.getPdfsDir(this)
            server = WifiServer(SERVER_PORT, pdfsDir).also { it.start() }
        }
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getLocalIp(): String {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }

    private fun buildNotification(): Notification {
        val ip = try { getLocalIp() } catch (e: Exception) { "127.0.0.1" }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ZenScreen activo")
            .setContentText("Dashboard: http://$ip:$SERVER_PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ZenScreen Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Servidor WiFi de ZenScreen" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
