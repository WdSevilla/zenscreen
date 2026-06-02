package com.freekiosk.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val DEFAULT_PIN = "1234"
    const val DEFAULT_INTERVAL_SECONDS = 10L
    const val DEFAULT_SERVER_PORT = 8080
    const val DEFAULT_SERVER_USER = "admin"
    const val DEFAULT_SERVER_PASS = "zenscreen"

    // Valores posibles: "portrait", "landscape", "sensor"
    const val DEFAULT_ORIENTATION = "portrait"

    private const val NAME = "freekiosk_prefs"
    private const val KEY_PIN = "admin_pin"
    private const val KEY_INTERVAL = "slideshow_interval"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SERVER_USER = "server_user"
    private const val KEY_SERVER_PASS = "server_pass"
    private const val KEY_LAUNCHER_ASKED = "launcher_asked"
    private const val KEY_PLAYLIST = "playlist_order"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getPin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, pin: String) =
        prefs(context).edit().putString(KEY_PIN, pin).apply()

    fun getIntervalSeconds(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL_SECONDS)

    fun setIntervalSeconds(context: Context, seconds: Long) =
        prefs(context).edit().putLong(KEY_INTERVAL, seconds).apply()

    fun getOrientation(context: Context): String =
        prefs(context).getString(KEY_ORIENTATION, DEFAULT_ORIENTATION) ?: DEFAULT_ORIENTATION

    fun setOrientation(context: Context, value: String) =
        prefs(context).edit().putString(KEY_ORIENTATION, value).apply()

    fun getServerPort(context: Context): Int =
        prefs(context).getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)

    fun setServerPort(context: Context, port: Int) =
        prefs(context).edit().putInt(KEY_SERVER_PORT, port).apply()

    fun getServerUser(context: Context): String =
        prefs(context).getString(KEY_SERVER_USER, DEFAULT_SERVER_USER) ?: DEFAULT_SERVER_USER

    fun setServerUser(context: Context, user: String) =
        prefs(context).edit().putString(KEY_SERVER_USER, user).apply()

    fun getServerPass(context: Context): String =
        prefs(context).getString(KEY_SERVER_PASS, DEFAULT_SERVER_PASS) ?: DEFAULT_SERVER_PASS

    fun setServerPass(context: Context, pass: String) =
        prefs(context).edit().putString(KEY_SERVER_PASS, pass).apply()

    fun hasAskedLauncher(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAUNCHER_ASKED, false)

    fun setLauncherAsked(context: Context) =
        prefs(context).edit().putBoolean(KEY_LAUNCHER_ASKED, true).apply()

    fun getPlaylistOrder(context: Context): List<String> {
        val stored = prefs(context).getString(KEY_PLAYLIST, "") ?: ""
        return if (stored.isEmpty()) emptyList() else stored.split("\n")
    }

    fun setPlaylistOrder(context: Context, order: List<String>) {
        prefs(context).edit()
            .putString(KEY_PLAYLIST, order.joinToString("\n"))
            .apply()
    }
}
