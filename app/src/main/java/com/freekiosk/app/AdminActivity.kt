package com.freekiosk.app

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.freekiosk.app.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { importPdf(it) }; refreshFileCount() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // El admin siempre en portrait para facilitar el uso
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setupServerUrl()
        refreshFileCount()
        setupSlider()
        setupOrientationButtons()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshFileCount()
        setupServerUrl()
        updateLauncherButton()
    }

    private fun setupServerUrl() {
        val ip = getLocalIp()
        binding.serverUrlText.text = "http://$ip:${ServerService.SERVER_PORT}"
    }

    private fun getLocalIp(): String {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private fun refreshFileCount() {
        val dir = FileManager.getPdfsDir(this)
        val count = FileManager.listPdfs(dir).size
        binding.fileCountText.text = "$count PDF(s) en la tablet"
    }

    private fun importPdf(uri: Uri) {
        val dir = FileManager.getPdfsDir(this)
        val name = getFileName(uri) ?: "import_${System.currentTimeMillis()}.pdf"
        contentResolver.openInputStream(uri)?.use { input ->
            FileManager.savePdf(dir, name, input.readBytes())
        }
    }

    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun setupSlider() {
        val current = Prefs.getIntervalSeconds(this).toFloat()
        binding.intervalSlider.value = current.coerceIn(3f, 60f)
        binding.intervalLabel.text = "${current.toInt()}s"
        binding.intervalSlider.addOnChangeListener { _, value, _ ->
            binding.intervalLabel.text = "${value.toInt()}s"
            Prefs.setIntervalSeconds(this, value.toLong())
        }
    }

    private fun setupOrientationButtons() {
        highlightOrientation(Prefs.getOrientation(this))

        binding.btnOrientPortrait.setOnClickListener {
            Prefs.setOrientation(this, "portrait")
            highlightOrientation("portrait")
        }
        binding.btnOrientLandscape.setOnClickListener {
            Prefs.setOrientation(this, "landscape")
            highlightOrientation("landscape")
        }
        binding.btnOrientSensor.setOnClickListener {
            Prefs.setOrientation(this, "sensor")
            highlightOrientation("sensor")
        }
    }

    private fun highlightOrientation(selected: String) {
        val active   = ColorStateList.valueOf(0xFF4f8ef7.toInt())
        val inactive = ColorStateList.valueOf(0xFF22263a.toInt())
        binding.btnOrientPortrait.backgroundTintList  = if (selected == "portrait")  active else inactive
        binding.btnOrientLandscape.backgroundTintList = if (selected == "landscape") active else inactive
        binding.btnOrientSensor.backgroundTintList    = if (selected == "sensor")    active else inactive
    }

    private fun setupButtons() {
        binding.importUsbBtn.setOnClickListener {
            pickPdf.launch("application/pdf")
        }
        binding.setLauncherBtn.setOnClickListener {
            requestDefaultLauncher()
        }
        binding.backToKioskBtn.setOnClickListener {
            finish()
        }
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                    REQ_HOME_ROLE
                )
            }
        } else {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_HOME_ROLE) updateLauncherButton()
    }

    private fun updateLauncherButton() {
        if (isDefaultLauncher()) {
            binding.setLauncherBtn.text = "✓ ZenScreen es el launcher activo"
            binding.setLauncherBtn.backgroundTintList = ColorStateList.valueOf(0xFF166534.toInt())
        } else {
            binding.setLauncherBtn.text = "Establecer como launcher"
            binding.setLauncherBtn.backgroundTintList = ColorStateList.valueOf(0xFF22263a.toInt())
        }
    }

    companion object {
        private const val REQ_HOME_ROLE = 101
    }
}
