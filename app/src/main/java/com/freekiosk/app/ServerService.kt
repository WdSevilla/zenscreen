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
        const val ACTION_RESTART = "com.freekiosk.app.RESTART_SERVER"
        private const val CHANNEL_ID = "freekiosk_server"
        private const val NOTIF_ID = 1

        fun getPort(context: android.content.Context) = Prefs.getServerPort(context)
    }

    private var server: WifiServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = Prefs.getServerPort(this)
        // Reiniciar si se pidió explícitamente o el puerto cambió
        val portChanged = server?.isAlive == true && server?.listeningPort != port
        if (intent?.action == ACTION_RESTART || portChanged) {
            server?.stop()
            server = null
        }
        if (server == null || !server!!.isAlive) {
            val pdfsDir = FileManager.getPdfsDir(this)
            server = WifiServer(port, pdfsDir).also { it.start() }
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
            .setContentText("Dashboard: http://$ip:${Prefs.getServerPort(this)}")
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
