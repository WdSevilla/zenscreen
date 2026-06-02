package com.freekiosk.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.freekiosk.app.databinding.ActivityKioskBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private var slideshow = SlideshowManager<File>(emptyList())
    private var slideshowJob: Job? = null
    private var renderJob: Job? = null          // job del render activo
    private var currentRenderer: PdfPageRenderer? = null
    private val renderMutex = Mutex()           // serializa acceso al renderer
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()
        tryLockTask()
        startServer()
        setupLongPressAdmin()
        setupTapToAdvance()
    }

    override fun onResume() {
        super.onResume()
        applyOrientation()
        reloadAndStart()
    }

    override fun onDestroy() {
        slideshowJob?.cancel()
        renderJob?.cancel()
        currentRenderer?.close()
        scope.cancel()
        super.onDestroy()
    }

    // Bloquear botón Atrás
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* bloqueado */ }

    // Re-ocultar barras si el usuario desliza desde el borde
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    // Intentar activar pin task (Screen Pinning) al arrancar
    private fun tryLockTask() {
        try {
            startLockTask()
        } catch (e: Exception) {
            // Si el dispositivo no lo permite, continúa sin bloqueo
        }
    }

    private fun applyOrientation() {
        val desired = when (Prefs.getOrientation(this)) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "sensor"    -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else        -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Solo cambiar si es diferente — evita loop de recreación
        if (requestedOrientation != desired) requestedOrientation = desired
    }

    private fun hideSystemUI() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startServer() {
        startForegroundService(Intent(this, ServerService::class.java))
    }

    private fun reloadAndStart() {
        val dir = FileManager.getPdfsDir(this)
        val files = FileManager.listPdfs(dir)
        slideshow.reload(files)

        if (slideshow.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.pdfImageView.visibility = View.GONE
            return
        }

        binding.emptyText.visibility = View.GONE
        binding.pdfImageView.visibility = View.VISIBLE
        renderCurrent()
        startAutoAdvance()
    }

    private fun renderCurrent() {
        if (slideshow.isEmpty()) return
        val file = slideshow.current()

        // Cancelar render anterior antes de iniciar uno nuevo
        renderJob?.cancel()
        renderJob = scope.launch(Dispatchers.IO) {
            val w = binding.pdfImageView.width.takeIf { it > 0 } ?: 1080
            val h = binding.pdfImageView.height.takeIf { it > 0 } ?: 1920
            renderMutex.withLock {
                if (!isActive) return@withLock
                try {
                    currentRenderer?.close()
                    currentRenderer = null
                    val renderer = PdfPageRenderer(file)
                    currentRenderer = renderer
                    val bitmap = renderer.renderPage(0, w, h)
                    if (isActive) {
                        withContext(Dispatchers.Main) {
                            binding.pdfImageView.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        withContext(Dispatchers.Main) { advance() }
                    }
                }
            }
        }
    }

    private fun startAutoAdvance() {
        slideshowJob?.cancel()
        val interval = Prefs.getIntervalSeconds(this) * 1000L
        slideshowJob = scope.launch {
            while (isActive) {
                delay(interval)
                advance()
            }
        }
    }

    private fun advance() {
        slideshow.next()
        renderCurrent()
    }

    private fun setupTapToAdvance() {
        binding.pdfImageView.setOnClickListener {
            slideshowJob?.cancel()
            advance()
            startAutoAdvance()
        }
    }

    private fun setupLongPressAdmin() {
        binding.adminTrigger.setOnLongClickListener {
            PinDialogFragment.newInstance {
                startActivity(Intent(this, AdminActivity::class.java))
            }.show(supportFragmentManager, PinDialogFragment.TAG)
            true
        }
    }
}
