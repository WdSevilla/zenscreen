package com.zenscreen.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
    }

    // ── Permisos ────────────────────────────────────────────────────────────

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!granted(Manifest.permission.READ_MEDIA_IMAGES))
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (!granted(Manifest.permission.POST_NOTIFICATIONS))
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (!granted(Manifest.permission.READ_EXTERNAL_STORAGE))
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        } else {
            requestHomeLauncherIfNeeded()
        }
    }

    private fun granted(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) requestHomeLauncherIfNeeded()
    }

    // ── Launcher por defecto ────────────────────────────────────────────────

    private fun requestHomeLauncherIfNeeded() {
        when {
            // Ya es el launcher — ir al kiosk directamente
            isDefaultLauncher() -> goToKiosk()
            // Ya preguntamos antes — no molestar de nuevo, ir al kiosk
            Prefs.hasAskedLauncher(this) -> goToKiosk()
            // Primera vez — abrir ajustes y marcar como preguntado
            else -> {
                Prefs.setLauncherAsked(this)
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                    goToKiosk()
                }
            }
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    override fun onResume() {
        super.onResume()
        // Al volver de ajustes, si ya es el launcher ir al kiosk
        if (isDefaultLauncher()) goToKiosk()
    }

    // ── Kiosk ───────────────────────────────────────────────────────────────

    private fun goToKiosk() {
        startActivity(Intent(this, KioskActivity::class.java))
        finish()
    }
}
