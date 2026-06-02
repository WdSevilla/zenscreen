package com.freekiosk.app

import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
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
        val current = Prefs.getOrientation(this)
        binding.btnOrientPortrait.isSelected  = current == "portrait"
        binding.btnOrientLandscape.isSelected = current == "landscape"
        binding.btnOrientSensor.isSelected    = current == "sensor"

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
        binding.btnOrientPortrait.isSelected  = selected == "portrait"
        binding.btnOrientLandscape.isSelected = selected == "landscape"
        binding.btnOrientSensor.isSelected    = selected == "sensor"
    }

    private fun setupButtons() {
        binding.importUsbBtn.setOnClickListener {
            pickPdf.launch("application/pdf")
        }
        binding.backToKioskBtn.setOnClickListener {
            finish()
        }
    }
}
