# Media Support (Video + Imágenes) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extender ZenScreen para mostrar imágenes y videos además de PDFs, con duración variable — PDFs e imágenes usan el intervalo configurado, videos duran lo que dura el video.

**Architecture:** Se introduce `MediaFile(file, type: MediaType)` como unidad central del slideshow. `FileManager` detecta el tipo por extensión. `KioskActivity` decide qué renderer usar según el tipo: `PdfPageRenderer` para PDFs, `BitmapFactory` para imágenes, y ExoPlayer (`PlayerView`) para videos. El timer existente sigue activo solo para PDF/imagen; para video se avanza via `Player.Listener.onPlaybackStateChanged`.

**Tech Stack:** Kotlin, AndroidX Media3 ExoPlayer 1.3.0, Android BitmapFactory (built-in), NanoHTTPD (existing).

---

## File Structure

```
Modify: app/build.gradle.kts                           ← agregar Media3
Modify: app/src/main/java/com/zenscreen/app/FileManager.kt   ← MediaType, MediaFile, listMedia, saveMedia
Modify: app/src/test/java/com/zenscreen/app/FileManagerTest.kt ← nuevos tests MediaType
Modify: app/src/main/res/layout/activity_kiosk.xml     ← agregar PlayerView
Modify: app/src/main/java/com/zenscreen/app/KioskActivity.kt  ← render por tipo, duración variable
Modify: app/src/main/java/com/zenscreen/app/WifiServer.kt     ← aceptar imagen/video en upload
Modify: app/src/main/java/com/zenscreen/app/AdminActivity.kt  ← mostrar conteo de todos los media
```

---

## Task 1: MediaType + MediaFile + FileManager.listMedia()

**Files:**
- Modify: `app/src/main/java/com/zenscreen/app/FileManager.kt`
- Modify: `app/src/test/java/com/zenscreen/app/FileManagerTest.kt`

- [ ] **Step 1: Agregar tests para MediaType y listMedia()**

```kotlin
// Al final de FileManagerTest.kt, agrega estos tests:

@Test
fun `mediaTypeOf returns PDF for pdf extension`() {
    assertEquals(MediaType.PDF, FileManager.mediaTypeOf(File("doc.pdf")))
}

@Test
fun `mediaTypeOf returns IMAGE for jpg, png, webp`() {
    assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("photo.jpg")))
    assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("photo.PNG")))
    assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("photo.webp")))
}

@Test
fun `mediaTypeOf returns VIDEO for mp4, mov, mkv`() {
    assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("clip.mp4")))
    assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("clip.MOV")))
    assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("clip.mkv")))
}

@Test
fun `mediaTypeOf returns null for unknown extension`() {
    assertNull(FileManager.mediaTypeOf(File("file.docx")))
}

@Test
fun `listMedia returns PDFs images and videos sorted by name`() {
    val dir = tmp.newFolder("media")
    File(dir, "c.mp4").createNewFile()
    File(dir, "b.jpg").createNewFile()
    File(dir, "a.pdf").createNewFile()
    File(dir, "ignore.docx").createNewFile()

    val result = FileManager.listMedia(dir)

    assertEquals(3, result.size)
    assertEquals("a.pdf",  result[0].file.name)
    assertEquals("b.jpg",  result[1].file.name)
    assertEquals("c.mp4",  result[2].file.name)
    assertEquals(MediaType.PDF,   result[0].type)
    assertEquals(MediaType.IMAGE, result[1].type)
    assertEquals(MediaType.VIDEO, result[2].type)
}

@Test
fun `saveMedia writes bytes and prevents path traversal`() {
    val dir = tmp.newFolder("media")
    val bytes = "data".toByteArray()

    FileManager.saveMedia(dir, "../evil.pdf", bytes)

    assertFalse(File(dir.parent, "evil.pdf").exists())
    assertTrue(File(dir, "evil.pdf").exists())
}
```

- [ ] **Step 2: Correr tests — deben fallar**

```bash
./gradlew :app:test 2>&1 | grep -E "FAILED|ERROR|mediaTypeOf|listMedia|saveMedia"
```
Esperado: múltiples FAILED — `MediaType`, `MediaFile`, `listMedia`, `saveMedia` no existen.

- [ ] **Step 3: Implementar MediaType, MediaFile y los nuevos métodos en FileManager.kt**

Reemplaza el contenido completo de `FileManager.kt`:

```kotlin
package com.zenscreen.app

import android.content.Context
import java.io.File

enum class MediaType { PDF, IMAGE, VIDEO }

data class MediaFile(val file: File, val type: MediaType)

object FileManager {

    private val PDF_EXTS   = setOf("pdf")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")

    fun getPdfsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "pdfs")
        dir.mkdirs()
        return dir
    }

    /** Tipo de archivo según extensión. Null si no está soportado. */
    fun mediaTypeOf(file: File): MediaType? {
        return when (file.extension.lowercase()) {
            in PDF_EXTS   -> MediaType.PDF
            in IMAGE_EXTS -> MediaType.IMAGE
            in VIDEO_EXTS -> MediaType.VIDEO
            else          -> null
        }
    }

    /** Lista todos los archivos de media soportados, ordenados por nombre. */
    fun listMedia(dir: File): List<MediaFile> =
        dir.listFiles()
            ?.mapNotNull { f -> mediaTypeOf(f)?.let { MediaFile(f, it) } }
            ?.sortedBy { it.file.name }
            ?: emptyList()

    /** Compatibilidad retroactiva: solo PDFs. */
    fun listPdfs(dir: File): List<File> =
        listMedia(dir).filter { it.type == MediaType.PDF }.map { it.file }

    fun deleteFile(file: File) {
        file.delete()
    }

    fun savePdf(dir: File, name: String, bytes: ByteArray) = saveMedia(dir, name, bytes)

    fun saveMedia(dir: File, name: String, bytes: ByteArray) {
        val safe = File(name).name  // evitar path traversal
        File(dir, safe).writeBytes(bytes)
    }
}
```

- [ ] **Step 4: Correr tests — deben pasar**

```bash
./gradlew :app:test 2>&1 | grep -E "PASSED|FAILED|tests"
```
Esperado: todos los tests de `FileManagerTest` en PASSED.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zenscreen/app/FileManager.kt \
        app/src/test/java/com/zenscreen/app/FileManagerTest.kt
git commit -m "feat: MediaType enum + MediaFile + FileManager.listMedia()"
```

---

## Task 2: Dependencia ExoPlayer + PlayerView en layout

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/layout/activity_kiosk.xml`

- [ ] **Step 1: Agregar Media3 a app/build.gradle.kts**

En el bloque `dependencies { }`, agrega después de las dependencias existentes:

```kotlin
// ExoPlayer para reproducción de video
implementation("androidx.media3:media3-exoplayer:1.3.0")
implementation("androidx.media3:media3-ui:1.3.0")
```

- [ ] **Step 2: Sincronizar y verificar que compila**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Agregar PlayerView al layout activity_kiosk.xml**

El layout actual tiene `FrameLayout` con `pdfImageView`, `adminTrigger` y `emptyText`.
Agrega `PlayerView` después de `pdfImageView` (inicialmente `GONE`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- PDFs e imágenes -->
    <ImageView
        android:id="@+id/pdfImageView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitCenter"
        android:contentDescription="Media page" />

    <!-- Videos -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/videoPlayerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        app:use_controller="false"
        app:show_buffering="when_playing"
        xmlns:app="http://schemas.android.com/apk/res-auto" />

    <!-- Zona de toque largo invisible — esquina superior derecha -->
    <View
        android:id="@+id/adminTrigger"
        android:layout_width="80dp"
        android:layout_height="80dp"
        android:layout_gravity="top|end"
        android:background="@android:color/transparent" />

    <!-- Mensaje sin archivos -->
    <TextView
        android:id="@+id/emptyText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Sin archivos. Abre el panel admin para subir contenido."
        android:textColor="#64748b"
        android:textSize="16sp"
        android:padding="32dp"
        android:gravity="center"
        android:visibility="gone" />

</FrameLayout>
```

- [ ] **Step 4: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/layout/activity_kiosk.xml
git commit -m "feat: add ExoPlayer dependency and PlayerView to kiosk layout"
```

---

## Task 3: KioskActivity — render por tipo y duración variable

**Files:**
- Modify: `app/src/main/java/com/zenscreen/app/KioskActivity.kt`

- [ ] **Step 1: Reemplazar KioskActivity.kt completo**

```kotlin
package com.zenscreen.app

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
import com.zenscreen.app.databinding.ActivityKioskBinding
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

    private fun tryLockTask() {
        try { startLockTask() } catch (e: Exception) { }
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
        slideshowJob?.cancel()          // suspender el timer — el video dicta el ritmo
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
```

- [ ] **Step 2: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zenscreen/app/KioskActivity.kt
git commit -m "feat: KioskActivity renders PDF/image/video with variable duration"
```

---

## Task 4: WifiServer — aceptar imágenes y videos en upload

**Files:**
- Modify: `app/src/main/java/com/zenscreen/app/WifiServer.kt`

- [ ] **Step 1: Actualizar handleUpload y serveFileList en WifiServer.kt**

Localiza `handleUpload` y reemplaza la lógica de validación de extensión:

```kotlin
private fun handleUpload(session: IHTTPSession): Response {
    return try {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val tmpPath = files["file"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file"
        )
        val filename = session.parameters["file"]?.firstOrNull() ?: "upload.bin"
        val safeName = File(filename).name
        // Validar que la extensión esté soportada
        if (FileManager.mediaTypeOf(File(safeName)) == null) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Formato no soportado. Usa: PDF, JPG, PNG, WebP, MP4, MKV, MOV"
            )
        }
        File(tmpPath).copyTo(File(pdfsDir, safeName), overwrite = true)
        newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
    } catch (e: Exception) {
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
    }
}
```

Actualiza `serveFileList` para incluir todos los archivos de media (no solo PDFs):

```kotlin
private fun serveFileList(): Response {
    val files = FileManager.listMedia(pdfsDir)
    val json = buildString {
        append("""{"files":[""")
        files.forEachIndexed { i, mf ->
            if (i > 0) append(",")
            val icon = when (mf.type) {
                MediaType.PDF   -> "📄"
                MediaType.IMAGE -> "🖼️"
                MediaType.VIDEO -> "🎬"
            }
            append("""{"name":"${mf.file.name}","size":${mf.file.length()},"type":"${mf.type.name.lowercase()}","icon":"$icon"}""")
        }
        append("]}")
    }
    return newFixedLengthResponse(Response.Status.OK, "application/json", json)
}
```

Actualiza también el HTML del dashboard en `serveDashboard()` — el `input` del uploader debe aceptar los nuevos formatos. Localiza la línea con `accept=".pdf"` y reemplaza:

```html
<input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.mp4,.mkv,.mov,.avi,.webm" multiple onchange="upload(this.files)">
```

Y actualiza el texto de ayuda debajo:

```html
<p style="color:#64748b">Arrastra archivos aquí o toca para seleccionar<br><small style="font-size:10px">PDF · JPG · PNG · WebP · MP4 · MKV · MOV</small></p>
```

- [ ] **Step 2: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zenscreen/app/WifiServer.kt
git commit -m "feat: WifiServer accepts images and videos, shows type icon in dashboard"
```

---

## Task 5: AdminActivity — conteo de todos los archivos de media

**Files:**
- Modify: `app/src/main/java/com/zenscreen/app/AdminActivity.kt`

- [ ] **Step 1: Actualizar refreshFileCount para mostrar conteo por tipo**

Localiza `refreshFileCount()` en `AdminActivity.kt` y reemplaza:

```kotlin
private fun refreshFileCount() {
    val dir = FileManager.getPdfsDir(this)
    val all = FileManager.listMedia(dir)
    val pdfs    = all.count { it.type == MediaType.PDF }
    val images  = all.count { it.type == MediaType.IMAGE }
    val videos  = all.count { it.type == MediaType.VIDEO }
    binding.fileCountText.text = buildString {
        append("${all.size} archivo(s) en la tablet")
        if (all.isNotEmpty()) {
            append("\n")
            val parts = mutableListOf<String>()
            if (pdfs   > 0) parts.add("📄 $pdfs PDF")
            if (images > 0) parts.add("🖼️ $images imagen${if (images > 1) "es" else ""}")
            if (videos > 0) parts.add("🎬 $videos video${if (videos > 1) "s" else ""}")
            append(parts.joinToString(" · "))
        }
    }
}
```

- [ ] **Step 2: Verificar build y tests**

```bash
./gradlew :app:assembleDebug :app:test 2>&1 | tail -8
```
Esperado: `BUILD SUCCESSFUL` y todos los tests en PASSED.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zenscreen/app/AdminActivity.kt
git commit -m "feat: AdminActivity shows media count broken down by type"
```

---

## Task 6: Build final y verificación en tablet

- [ ] **Step 1: Build limpio**

```bash
find app/build -name "* 2.*" -delete 2>/dev/null
./gradlew clean assembleDebug 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Instalar**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Verificar PDF**
- Sube un PDF desde el dashboard WiFi
- Debe mostrarse fullscreen y avanzar automáticamente al intervalo configurado

- [ ] **Step 4: Verificar imagen**
- Sube un JPG o PNG desde el dashboard
- Debe mostrarse centrado y avanzar al intervalo configurado

- [ ] **Step 5: Verificar video**
- Sube un MP4
- Debe reproducirse fullscreen sin controles
- Al terminar debe avanzar solo al siguiente archivo

- [ ] **Step 6: Verificar mezcla**
- Sube un PDF + imagen + video juntos
- El slideshow debe pasar por los tres, respetando la duración de cada tipo

- [ ] **Step 7: Commit final**

```bash
git add -A
git commit -m "chore: verify media support end-to-end"
```
