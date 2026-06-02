package com.freekiosk.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.freekiosk.app.databinding.ActivityKioskBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import java.io.File

class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private var slideshow = SlideshowManager<MediaFile>(emptyList())
    private var slideshowJob: Job? = null
    private var renderJob: Job? = null
    private var currentRenderer: PdfPageRenderer? = null
    private var exoPlayer: ExoPlayer? = null
    private val renderMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()
        startServer()
        setupLongPressAdmin()
        setupTapToAdvance()
    }

    override fun onResume() {
        super.onResume()
        applyOrientation()
        reloadAndStart()
    }

    override fun onPause() {
        super.onPause()
        slideshowJob?.cancel()
        renderJob?.cancel()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        slideshowJob?.cancel()
        renderJob?.cancel()
        currentRenderer?.close()
        releasePlayer()
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* bloqueado */ }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    // ── Sistema ─────────────────────────────────────────────────────────────

    private fun hideSystemUI() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startServer() {
        startForegroundService(Intent(this, ServerService::class.java))
    }

    private fun applyOrientation() {
        val desired = when (Prefs.getOrientation(this)) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "sensor"    -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else        -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (requestedOrientation != desired) requestedOrientation = desired
    }

    // ── Slideshow ────────────────────────────────────────────────────────────

    private fun reloadAndStart() {
        // Capturar antes de cualquier suspensión para que el timer no interfiera
        val currentName = if (!slideshow.isEmpty()) slideshow.current().file.name else null
        scope.launch {
            val dir   = withContext(Dispatchers.IO) { FileManager.getPdfsDir(this@KioskActivity) }
            val order = Prefs.getPlaylistOrder(this@KioskActivity)
            val files = withContext(Dispatchers.IO) { FileManager.listMediaOrdered(dir, order) }
            slideshow.reload(files)
            if (currentName != null) {
                val idx = files.indexOfFirst { it.file.name == currentName }
                if (idx >= 0) slideshow.seekTo(idx)
            }

            if (slideshow.isEmpty()) {
                binding.emptyText.visibility = View.VISIBLE
                binding.pdfImageView.visibility = View.GONE
                binding.videoPlayerView.visibility = View.GONE
                return@launch
            }

            binding.emptyText.visibility = View.GONE
            renderCurrent()
        }
    }

    private fun renderCurrent() {
        if (slideshow.isEmpty()) return
        val mediaFile = slideshow.current()

        renderJob?.cancel()
        renderJob = scope.launch {
            when (mediaFile.type) {
                MediaType.PDF   -> renderPdf(mediaFile.file)
                MediaType.IMAGE -> renderImage(mediaFile.file)
                MediaType.VIDEO -> renderVideo(mediaFile.file)
            }
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    private suspend fun renderPdf(file: File) {
        stopVideo()
        showImageView()

        withContext(Dispatchers.IO) {
            val w = binding.pdfImageView.width.takeIf  { it > 0 } ?: 1080
            val h = binding.pdfImageView.height.takeIf { it > 0 } ?: 1920
            renderMutex.withLock {
                if (!isActive) return@withLock
                try {
                    currentRenderer?.close()
                    currentRenderer = null
                    val renderer = PdfPageRenderer(file)
                    currentRenderer = renderer
                    val bitmap = renderer.renderPage(0, w, h)
                    if (isActive) withContext(Dispatchers.Main) {
                        binding.pdfImageView.setImageBitmap(bitmap)
                        fadeInImage()
                    }
                } catch (e: Exception) {
                    if (isActive) withContext(Dispatchers.Main) { advance() }
                }
            }
        }
        if (coroutineContext.isActive) startTimerAdvance()
    }

    // ── Imagen ───────────────────────────────────────────────────────────────

    private suspend fun renderImage(file: File) {
        stopVideo()
        showImageView()

        withContext(Dispatchers.IO) {
            renderMutex.withLock {
                if (!isActive) return@withLock
                try {
                    currentRenderer?.close()
                    currentRenderer = null
                    val w = binding.pdfImageView.width.takeIf  { it > 0 } ?: 1080
                    val h = binding.pdfImageView.height.takeIf { it > 0 } ?: 1920
                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                        BitmapFactory.decodeFile(file.absolutePath, this)
                        inSampleSize = calculateInSampleSize(outWidth, outHeight, w, h)
                        inJustDecodeBounds = false
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (isActive && bitmap != null) withContext(Dispatchers.Main) {
                        binding.pdfImageView.setImageBitmap(bitmap)
                        fadeInImage()
                    } else if (isActive) {
                        withContext(Dispatchers.Main) { advance() }
                    }
                } catch (e: Exception) {
                    if (isActive) withContext(Dispatchers.Main) { advance() }
                }
            }
        }
        if (coroutineContext.isActive) startTimerAdvance()
    }

    private fun calculateInSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var sampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
            while (halfH / sampleSize >= reqH && halfW / sampleSize >= reqW) sampleSize *= 2
        }
        return sampleSize
    }

    // ── Video ────────────────────────────────────────────────────────────────

    private suspend fun renderVideo(file: File) {
        // Cerrar el renderer bajo el mutex para evitar race condition con renderPdf/renderImage
        withContext(Dispatchers.IO) {
            renderMutex.withLock {
                currentRenderer?.close()
                currentRenderer = null
            }
        }
        if (!coroutineContext.isActive) return

        slideshowJob?.cancel()
        showVideoView()
        releasePlayer()

        // ExoPlayer debe crearse y usarse en el hilo principal
        val player = ExoPlayer.Builder(this@KioskActivity).build()
        exoPlayer = player
        binding.videoPlayerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> fadeInVideo()
                    Player.STATE_ENDED -> advance()
                }
            }
        })

        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = true
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        binding.videoPlayerView.player = null
    }

    private fun stopVideo() {
        releasePlayer()
    }

    // ── Vistas ───────────────────────────────────────────────────────────────

    private fun showImageView() {
        binding.pdfImageView.setImageBitmap(null)
        binding.pdfImageView.alpha = 0f
        binding.pdfImageView.visibility = View.VISIBLE
        binding.videoPlayerView.visibility = View.GONE
    }

    private fun fadeInImage() {
        binding.pdfImageView.animate().alpha(1f).setDuration(400).start()
    }

    private fun showVideoView() {
        binding.videoPlayerView.alpha = 0f
        binding.pdfImageView.visibility = View.GONE
        binding.videoPlayerView.visibility = View.VISIBLE
    }

    private fun fadeInVideo() {
        binding.videoPlayerView.animate().alpha(1f).setDuration(400).start()
    }

    // ── Timer (PDF e imagen) ─────────────────────────────────────────────────

    private fun startTimerAdvance() {
        slideshowJob?.cancel()
        val interval = Prefs.getIntervalSeconds(this) * 1000L
        slideshowJob = scope.launch {
            delay(interval)
            if (isActive) advance()
        }
    }

    private fun advance() {
        slideshow.next()
        renderCurrent()
    }

    // ── Navegación táctil ────────────────────────────────────────────────────

    private fun setupTapToAdvance() {
        val tapListener = android.view.View.OnClickListener { v ->
            slideshowJob?.cancel()
            val mid = v.width / 2f
            // El último evento de toque se lee desde el root para obtener la X
            val x = lastTouchX
            if (x < mid) goBack() else advance()
        }
        binding.pdfImageView.setOnClickListener(tapListener)
        binding.videoPlayerView.setOnClickListener(tapListener)

        val touchListener = android.view.View.OnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
            }
            false
        }
        binding.pdfImageView.setOnTouchListener(touchListener)
        binding.videoPlayerView.setOnTouchListener(touchListener)
    }

    private var lastTouchX = 0f

    private fun goBack() {
        slideshow.prev()
        renderCurrent()
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
