package com.freekiosk.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val DEFAULT_PIN = "1234"
    const val DEFAULT_INTERVAL_SECONDS = 10L

    // Valores posibles: "portrait", "landscape", "sensor"
    const val DEFAULT_ORIENTATION = "portrait"

    private const val NAME = "freekiosk_prefs"
    private const val KEY_PIN = "admin_pin"
    private const val KEY_INTERVAL = "slideshow_interval"
    private const val KEY_ORIENTATION = "orientation"

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
}
