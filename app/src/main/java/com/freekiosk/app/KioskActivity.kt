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
        val dir = FileManager.getPdfsDir(this)
        val files = FileManager.listMedia(dir)
        slideshow.reload(files)

        if (slideshow.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.pdfImageView.visibility = View.GONE
            binding.videoPlayerView.visibility = View.GONE
            return
        }

        binding.emptyText.visibility = View.GONE
        renderCurrent()
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
                    }
                } catch (e: Exception) {
                    if (isActive) withContext(Dispatchers.Main) { advance() }
                }
            }
        }
        startTimerAdvance()
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
                    } else if (isActive) {
                        withContext(Dispatchers.Main) { advance() }
                    }
                } catch (e: Exception) {
                    if (isActive) withContext(Dispatchers.Main) { advance() }
                }
            }
        }
        startTimerAdvance()
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

    private fun renderVideo(file: File) {
        currentRenderer?.close()
        currentRenderer = null
        slideshowJob?.cancel()  // video dicta el ritmo, no el timer
        showVideoView()

        releasePlayer()
        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        binding.videoPlayerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) advance()
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
        binding.pdfImageView.visibility = View.VISIBLE
        binding.videoPlayerView.visibility = View.GONE
    }

    private fun showVideoView() {
        binding.pdfImageView.visibility = View.GONE
        binding.videoPlayerView.visibility = View.VISIBLE
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
        binding.pdfImageView.setOnClickListener {
            slideshowJob?.cancel()
            advance()
        }
        binding.videoPlayerView.setOnClickListener {
            slideshowJob?.cancel()
            advance()
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
