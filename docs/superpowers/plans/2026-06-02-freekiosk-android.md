# FreeKiosk Android App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-contained Android kiosk app that displays PDFs fullscreen in a slideshow loop, with hidden admin access via long-press + PIN, WiFi upload dashboard, and auto-start on boot.

**Architecture:** Single Android app with two Activities — KioskActivity (fullscreen PDF slideshow, always-on) and AdminActivity (PIN-protected management panel). An embedded NanoHTTPD server runs in a foreground Service to accept WiFi file uploads. A BroadcastReceiver handles auto-start on boot.

**Tech Stack:** Kotlin, Android SDK (minSdk 26 / API 8.0+), Android PdfRenderer (built-in), NanoHTTPD 2.3.1, SharedPreferences, Foreground Service, startLockTask() for kiosk lock.

---

## File Structure

```
freekiosk-android/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/freekiosk/app/
│   │   │   │   ├── MainActivity.kt           ← Entry point: decides kiosk vs admin
│   │   │   │   ├── KioskActivity.kt          ← Fullscreen PDF slideshow
│   │   │   │   ├── AdminActivity.kt          ← Management panel
│   │   │   │   ├── PinDialogFragment.kt      ← PIN entry dialog
│   │   │   │   ├── ServerService.kt          ← Foreground service con NanoHTTPD
│   │   │   │   ├── WifiServer.kt             ← NanoHTTPD: upload + dashboard HTML
│   │   │   │   ├── SlideshowManager.kt       ← Timer + orden de PDFs
│   │   │   │   ├── PdfPageRenderer.kt        ← Wrapper de Android PdfRenderer
│   │   │   │   ├── FileManager.kt            ← Operaciones en /files/FreeKiosk/
│   │   │   │   ├── Prefs.kt                  ← SharedPreferences wrapper
│   │   │   │   └── BootReceiver.kt           ← Auto-start al encender
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       │   ├── activity_kiosk.xml
│   │   │       │   ├── activity_admin.xml
│   │   │       │   └── dialog_pin.xml
│   │   │       ├── drawable/
│   │   │       │   └── ic_notification.xml
│   │   │       └── values/
│   │   │           ├── strings.xml
│   │   │           └── colors.xml
│   │   └── test/
│   │       └── java/com/freekiosk/app/
│   │           ├── PrefsTest.kt
│   │           ├── FileManagerTest.kt
│   │           └── SlideshowManagerTest.kt
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Task 1: Proyecto Android + dependencias

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`

- [ ] **Step 1: Crear estructura base del proyecto**

```bash
mkdir -p freekiosk-android/app/src/main/java/com/freekiosk/app
mkdir -p freekiosk-android/app/src/main/res/layout
mkdir -p freekiosk-android/app/src/main/res/drawable
mkdir -p freekiosk-android/app/src/main/res/values
mkdir -p freekiosk-android/app/src/test/java/com/freekiosk/app
```

- [ ] **Step 2: Crear `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FreeKiosk"
include(":app")
```

- [ ] **Step 3: Crear `build.gradle.kts` (root)**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 4: Crear `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.freekiosk.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.freekiosk.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // NanoHTTPD para servidor WiFi embebido
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Coroutines para slideshow timer
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

- [ ] **Step 5: Commit**

```bash
git init
git add settings.gradle.kts build.gradle.kts app/build.gradle.kts
git commit -m "chore: project scaffold with dependencies"
```

---

## Task 2: Prefs — wrapper de SharedPreferences

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/Prefs.kt`
- Create: `app/src/test/java/com/freekiosk/app/PrefsTest.kt`

- [ ] **Step 1: Escribir el test**

```kotlin
// app/src/test/java/com/freekiosk/app/PrefsTest.kt
package com.freekiosk.app

import org.junit.Assert.*
import org.junit.Test

class PrefsTest {
    @Test
    fun `default PIN is 1234`() {
        // Prefs usa valores por defecto cuando no hay SharedPreferences real
        assertEquals("1234", Prefs.DEFAULT_PIN)
    }

    @Test
    fun `default slideshow interval is 10 seconds`() {
        assertEquals(10L, Prefs.DEFAULT_INTERVAL_SECONDS)
    }
}
```

- [ ] **Step 2: Correr test — debe fallar**

```bash
./gradlew test --tests "com.freekiosk.app.PrefsTest"
```
Esperado: FAIL — `Prefs` no existe.

- [ ] **Step 3: Implementar `Prefs.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/Prefs.kt
package com.freekiosk.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val DEFAULT_PIN = "1234"
    const val DEFAULT_INTERVAL_SECONDS = 10L

    private const val NAME = "freekiosk_prefs"
    private const val KEY_PIN = "admin_pin"
    private const val KEY_INTERVAL = "slideshow_interval"

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
}
```

- [ ] **Step 4: Correr test — debe pasar**

```bash
./gradlew test --tests "com.freekiosk.app.PrefsTest"
```
Esperado: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/Prefs.kt \
        app/src/test/java/com/freekiosk/app/PrefsTest.kt
git commit -m "feat: Prefs wrapper for PIN and slideshow interval"
```

---

## Task 3: FileManager — gestión de PDFs en almacenamiento

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/FileManager.kt`
- Create: `app/src/test/java/com/freekiosk/app/FileManagerTest.kt`

- [ ] **Step 1: Escribir el test**

```kotlin
// app/src/test/java/com/freekiosk/app/FileManagerTest.kt
package com.freekiosk.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `listPdfs returns only pdf files sorted by name`() {
        val dir = tmp.newFolder("pdfs")
        File(dir, "b.pdf").createNewFile()
        File(dir, "a.pdf").createNewFile()
        File(dir, "image.png").createNewFile()

        val result = FileManager.listPdfs(dir)

        assertEquals(2, result.size)
        assertEquals("a.pdf", result[0].name)
        assertEquals("b.pdf", result[1].name)
    }

    @Test
    fun `listPdfs returns empty list when dir is empty`() {
        val dir = tmp.newFolder("empty")
        assertEquals(emptyList<File>(), FileManager.listPdfs(dir))
    }

    @Test
    fun `deleteFile removes file from disk`() {
        val dir = tmp.newFolder("pdfs")
        val file = File(dir, "test.pdf").also { it.createNewFile() }

        FileManager.deleteFile(file)

        assertFalse(file.exists())
    }

    @Test
    fun `savePdf writes bytes to destination`() {
        val dir = tmp.newFolder("pdfs")
        val bytes = "PDF content".toByteArray()

        FileManager.savePdf(dir, "new.pdf", bytes)

        val saved = File(dir, "new.pdf")
        assertTrue(saved.exists())
        assertArrayEquals(bytes, saved.readBytes())
    }
}
```

- [ ] **Step 2: Correr test — debe fallar**

```bash
./gradlew test --tests "com.freekiosk.app.FileManagerTest"
```
Esperado: FAIL — `FileManager` no existe.

- [ ] **Step 3: Implementar `FileManager.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/FileManager.kt
package com.freekiosk.app

import android.content.Context
import java.io.File

object FileManager {

    fun getPdfsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "pdfs")
        dir.mkdirs()
        return dir
    }

    fun listPdfs(dir: File): List<File> =
        dir.listFiles { f -> f.extension.lowercase() == "pdf" }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun deleteFile(file: File) {
        file.delete()
    }

    fun savePdf(dir: File, name: String, bytes: ByteArray) {
        val safe = File(name).name  // evitar path traversal
        File(dir, safe).writeBytes(bytes)
    }
}
```

- [ ] **Step 4: Correr test — debe pasar**

```bash
./gradlew test --tests "com.freekiosk.app.FileManagerTest"
```
Esperado: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/FileManager.kt \
        app/src/test/java/com/freekiosk/app/FileManagerTest.kt
git commit -m "feat: FileManager for PDF listing, saving and deleting"
```

---

## Task 4: SlideshowManager — timer y orden de PDFs

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/SlideshowManager.kt`
- Create: `app/src/test/java/com/freekiosk/app/SlideshowManagerTest.kt`

- [ ] **Step 1: Escribir el test**

```kotlin
// app/src/test/java/com/freekiosk/app/SlideshowManagerTest.kt
package com.freekiosk.app

import org.junit.Assert.*
import org.junit.Test

class SlideshowManagerTest {

    @Test
    fun `next cycles through files in order`() {
        val mgr = SlideshowManager(listOf("a", "b", "c"))
        assertEquals("a", mgr.current())
        mgr.next()
        assertEquals("b", mgr.current())
        mgr.next()
        assertEquals("c", mgr.current())
        mgr.next()
        assertEquals("a", mgr.current()) // loop
    }

    @Test
    fun `prev goes back`() {
        val mgr = SlideshowManager(listOf("a", "b", "c"))
        mgr.next() // b
        mgr.prev()
        assertEquals("a", mgr.current())
    }

    @Test
    fun `prev from first wraps to last`() {
        val mgr = SlideshowManager(listOf("a", "b", "c"))
        mgr.prev()
        assertEquals("c", mgr.current())
    }

    @Test
    fun `isEmpty returns true for empty list`() {
        assertTrue(SlideshowManager(emptyList()).isEmpty())
    }

    @Test
    fun `reload updates list and resets index`() {
        val mgr = SlideshowManager(listOf("a", "b"))
        mgr.next()
        mgr.reload(listOf("x", "y", "z"))
        assertEquals("x", mgr.current())
        assertEquals(3, mgr.size())
    }
}
```

- [ ] **Step 2: Correr test — debe fallar**

```bash
./gradlew test --tests "com.freekiosk.app.SlideshowManagerTest"
```
Esperado: FAIL.

- [ ] **Step 3: Implementar `SlideshowManager.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/SlideshowManager.kt
package com.freekiosk.app

class SlideshowManager<T>(items: List<T>) {
    private var list: List<T> = items.toList()
    private var index: Int = 0

    fun current(): T = list[index]
    fun isEmpty(): Boolean = list.isEmpty()
    fun size(): Int = list.size

    fun next() {
        if (list.isEmpty()) return
        index = (index + 1) % list.size
    }

    fun prev() {
        if (list.isEmpty()) return
        index = (index - 1 + list.size) % list.size
    }

    fun reload(items: List<T>) {
        list = items.toList()
        index = 0
    }
}
```

- [ ] **Step 4: Correr test — debe pasar**

```bash
./gradlew test --tests "com.freekiosk.app.SlideshowManagerTest"
```
Esperado: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/SlideshowManager.kt \
        app/src/test/java/com/freekiosk/app/SlideshowManagerTest.kt
git commit -m "feat: SlideshowManager with circular navigation and reload"
```

---

## Task 5: PdfPageRenderer — wrapper de Android PdfRenderer

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/PdfPageRenderer.kt`

> No unit test aquí: Android `PdfRenderer` requiere contexto real de dispositivo (instrumented test). Se valida visualmente en Task 8.

- [ ] **Step 1: Crear `PdfPageRenderer.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/PdfPageRenderer.kt
package com.freekiosk.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

class PdfPageRenderer(file: File) : Closeable {

    private val fd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(fd)

    val pageCount: Int get() = renderer.pageCount

    /**
     * Renderiza la página [pageIndex] en un Bitmap escalado a [targetWidth] x [targetHeight].
     * Mantiene aspect ratio y centra en fondo negro.
     */
    fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap {
        val page = renderer.openPage(pageIndex)
        val pageW = page.width.toFloat()
        val pageH = page.height.toFloat()

        val scale = minOf(targetWidth / pageW, targetHeight / pageH)
        val bmpW = (pageW * scale).toInt().coerceAtLeast(1)
        val bmpH = (pageH * scale).toInt().coerceAtLeast(1)

        val pageBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        pageBitmap.eraseColor(Color.WHITE)
        page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        // Centrar en canvas del tamaño de la pantalla
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        result.eraseColor(Color.BLACK)
        val canvas = Canvas(result)
        val left = ((targetWidth - bmpW) / 2f)
        val top = ((targetHeight - bmpH) / 2f)
        canvas.drawBitmap(pageBitmap, left, top, null)
        pageBitmap.recycle()

        return result
    }

    override fun close() {
        renderer.close()
        fd.close()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/PdfPageRenderer.kt
git commit -m "feat: PdfPageRenderer wrapping Android PdfRenderer"
```

---

## Task 6: WifiServer — NanoHTTPD con dashboard HTML

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/WifiServer.kt`

- [ ] **Step 1: Crear `WifiServer.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/WifiServer.kt
package com.freekiosk.app

import fi.iki.elonen.NanoHTTPD
import java.io.File

class WifiServer(
    port: Int,
    private val pdfsDir: File
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> serveDashboard()
            session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
            session.method == Method.POST && session.uri == "/delete" -> handleDelete(session)
            session.method == Method.GET && session.uri == "/api/files" -> serveFileList()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveDashboard(): Response {
        val html = buildString {
            append("""<!DOCTYPE html><html lang="es"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>FreeKiosk Admin</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:system-ui,sans-serif;background:#0f1117;color:#e2e8f0;padding:24px}
h1{font-size:20px;margin-bottom:24px;color:#4f8ef7}
.card{background:#1a1d27;border:1px solid #2d3148;border-radius:12px;padding:20px;margin-bottom:20px}
h2{font-size:13px;color:#64748b;text-transform:uppercase;letter-spacing:.5px;margin-bottom:14px}
.drop{border:2px dashed #2d3148;border-radius:10px;padding:32px;text-align:center;cursor:pointer;position:relative}
.drop input{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%}
.drop:hover{border-color:#4f8ef7}
.file-row{display:flex;align-items:center;justify-content:space-between;padding:10px 0;border-bottom:1px solid #2d3148}
.file-row:last-child{border:none}
.btn-del{background:rgba(239,68,68,.15);color:#ef4444;border:1px solid rgba(239,68,68,.2);border-radius:8px;padding:6px 12px;cursor:pointer;font-size:12px}
.btn-del:hover{opacity:.8}
.status{display:inline-block;width:8px;height:8px;border-radius:50%;background:#22c55e;margin-right:8px;animation:pulse 2s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
</style></head><body>
<h1>📺 FreeKiosk · Gestión WiFi</h1>
<div class="card">
  <h2><span class="status"></span>Servidor activo</h2>
  <p style="color:#64748b;font-size:13px">Archivos en la tablet</p>
</div>
<div class="card">
  <h2>Subir PDFs</h2>
  <div class="drop">
    <input type="file" accept=".pdf" multiple onchange="upload(this.files)">
    <p style="color:#64748b">Arrastra PDFs aquí o toca para seleccionar</p>
  </div>
</div>
<div class="card">
  <h2>Archivos (<span id="count">...</span>)</h2>
  <div id="list">Cargando...</div>
</div>
<script>
async function load(){
  const r=await fetch('/api/files');
  const d=await r.json();
  document.getElementById('count').textContent=d.files.length;
  document.getElementById('list').innerHTML=d.files.length===0
    ?'<p style="color:#64748b;font-size:13px">Sin archivos todavía</p>'
    :d.files.map(f=>`<div class="file-row">
      <span style="font-size:14px">📄 ${f.name} <span style="color:#64748b;font-size:11px">(${(f.size/1024).toFixed(1)} KB)</span></span>
      <button class="btn-del" onclick="del('${f.name}')">Borrar</button>
    </div>`).join('');
}
async function upload(files){
  for(const f of files){
    const fd=new FormData();fd.append('file',f);
    await fetch('/upload',{method:'POST',body:fd});
  }
  load();
}
async function del(name){
  if(!confirm('¿Borrar '+name+'?'))return;
  await fetch('/delete',{method:'POST',body:name,headers:{'Content-Type':'text/plain'}});
  load();
}
load();
</script></body></html>""")
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        return try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val tmpPath = files["file"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file"
            )
            val filename = session.parameters["file"]?.firstOrNull() ?: "upload.pdf"
            val safeName = File(filename).name
            File(tmpPath).copyTo(File(pdfsDir, safeName), overwrite = true)
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    private fun handleDelete(session: IHTTPSession): Response {
        return try {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            val name = body["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No name"
            )
            val safeName = File(name).name
            File(pdfsDir, safeName).delete()
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    private fun serveFileList(): Response {
        val files = FileManager.listPdfs(pdfsDir)
        val json = buildString {
            append("""{"files":[""")
            files.forEachIndexed { i, f ->
                if (i > 0) append(",")
                append("""{"name":"${f.name}","size":${f.length()}}""")
            }
            append("]}")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/WifiServer.kt
git commit -m "feat: WifiServer with upload/delete dashboard via NanoHTTPD"
```

---

## Task 7: ServerService — Foreground Service para NanoHTTPD

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/ServerService.kt`
- Create: `app/src/main/res/drawable/ic_notification.xml`

- [ ] **Step 1: Crear ícono de notificación**

```xml
<!-- app/src/main/res/drawable/ic_notification.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M21,3L3,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,19L3,19L3,5h18v14z"/>
</vector>
```

- [ ] **Step 2: Crear `ServerService.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/ServerService.kt
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
        const val SERVER_PORT = 8080
        private const val CHANNEL_ID = "freekiosk_server"
        private const val NOTIF_ID = 1
    }

    private var server: WifiServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pdfsDir = FileManager.getPdfsDir(this)
        server = WifiServer(SERVER_PORT, pdfsDir).also { it.start() }
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
            .setContentTitle("FreeKiosk activo")
            .setContentText("Dashboard: http://$ip:$SERVER_PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FreeKiosk Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Servidor WiFi de FreeKiosk" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/ServerService.kt \
        app/src/main/res/drawable/ic_notification.xml
git commit -m "feat: ServerService foreground service wrapping WifiServer"
```

---

## Task 8: BootReceiver — auto-start al encender

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/BootReceiver.kt`

- [ ] **Step 1: Crear `BootReceiver.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/BootReceiver.kt
package com.freekiosk.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Arrancar el servidor
            context.startForegroundService(
                Intent(context, ServerService::class.java)
            )
            // Arrancar la actividad kiosk
            val kioskIntent = Intent(context, KioskActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(kioskIntent)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/BootReceiver.kt
git commit -m "feat: BootReceiver starts kiosk and server on device boot"
```

---

## Task 9: Layouts XML

**Files:**
- Create: `app/src/main/res/layout/activity_kiosk.xml`
- Create: `app/src/main/res/layout/activity_admin.xml`
- Create: `app/src/main/res/layout/dialog_pin.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: `activity_kiosk.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <ImageView
        android:id="@+id/pdfImageView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitCenter"
        android:contentDescription="PDF page" />

    <!-- Zona de toque largo invisible en esquina superior derecha -->
    <View
        android:id="@+id/adminTrigger"
        android:layout_width="80dp"
        android:layout_height="80dp"
        android:layout_gravity="top|end"
        android:background="@android:color/transparent" />

    <TextView
        android:id="@+id/emptyText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Sin PDFs. Abre el panel admin para subir archivos."
        android:textColor="#64748b"
        android:textSize="16sp"
        android:padding="32dp"
        android:gravity="center"
        android:visibility="gone" />

</FrameLayout>
```

- [ ] **Step 2: `activity_admin.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#0f1117"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="FreeKiosk · Admin"
        android:textColor="#4f8ef7"
        android:textSize="20sp"
        android:textStyle="bold"
        android:layout_marginBottom="24dp" />

    <!-- Tarjeta: Estado del servidor -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="#1a1d27"
        android:padding="16dp"
        android:layout_marginBottom="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="SERVIDOR WIFI"
            android:textColor="#64748b"
            android:textSize="11sp"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/serverUrlText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Obteniendo IP..."
            android:textColor="#4f8ef7"
            android:textSize="14sp"
            android:fontFamily="monospace" />
    </LinearLayout>

    <!-- Tarjeta: Archivos -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="#1a1d27"
        android:padding="16dp"
        android:layout_marginBottom="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="ARCHIVOS EN TABLET"
            android:textColor="#64748b"
            android:textSize="11sp"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/fileCountText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="..."
            android:textColor="#e2e8f0"
            android:textSize="14sp"
            android:layout_marginBottom="12dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/importUsbBtn"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Importar desde almacenamiento"
            android:backgroundTint="#22263a" />
    </LinearLayout>

    <!-- Tarjeta: Slideshow -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="#1a1d27"
        android:padding="16dp"
        android:layout_marginBottom="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="INTERVALO SLIDESHOW (segundos)"
            android:textColor="#64748b"
            android:textSize="11sp"
            android:layout_marginBottom="8dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <com.google.android.material.slider.Slider
                android:id="@+id/intervalSlider"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:valueFrom="3"
                android:valueTo="60"
                android:stepSize="1" />

            <TextView
                android:id="@+id/intervalLabel"
                android:layout_width="40dp"
                android:layout_height="wrap_content"
                android:textColor="#e2e8f0"
                android:textSize="14sp"
                android:gravity="end" />
        </LinearLayout>
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/backToKioskBtn"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Volver al kiosk"
        android:backgroundTint="#4f8ef7"
        android:layout_marginTop="8dp" />

</LinearLayout>
```

- [ ] **Step 3: `dialog_pin.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp"
    android:background="#1a1d27">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="PIN de administrador"
        android:textColor="#e2e8f0"
        android:textSize="16sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <com.google.android.material.textfield.TextInputLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/pinInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="numberPassword"
            android:hint="Ingresa el PIN"
            android:maxLength="8" />
    </com.google.android.material.textfield.TextInputLayout>

</LinearLayout>
```

- [ ] **Step 4: `strings.xml` y `colors.xml`**

```xml
<!-- app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">FreeKiosk</string>
    <string name="pin_wrong">PIN incorrecto</string>
    <string name="confirm">Confirmar</string>
    <string name="cancel">Cancelar</string>
</resources>
```

```xml
<!-- app/src/main/res/values/colors.xml -->
<resources>
    <color name="bg">#0f1117</color>
    <color name="surface">#1a1d27</color>
    <color name="accent">#4f8ef7</color>
    <color name="text">#e2e8f0</color>
    <color name="muted">#64748b</color>
</resources>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/
git commit -m "feat: XML layouts for kiosk, admin and PIN dialog"
```

---

## Task 10: PinDialogFragment

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/PinDialogFragment.kt`

- [ ] **Step 1: Crear `PinDialogFragment.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/PinDialogFragment.kt
package com.freekiosk.app

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.freekiosk.app.databinding.DialogPinBinding

class PinDialogFragment : DialogFragment() {

    var onSuccess: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPinBinding.inflate(layoutInflater)

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val entered = binding.pinInput.text?.toString() ?: ""
                val stored = Prefs.getPin(requireContext())
                if (entered == stored) {
                    onSuccess?.invoke()
                } else {
                    Toast.makeText(requireContext(), R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }

    companion object {
        const val TAG = "PinDialog"
        fun newInstance(onSuccess: () -> Unit) =
            PinDialogFragment().also { it.onSuccess = onSuccess }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/PinDialogFragment.kt
git commit -m "feat: PinDialogFragment for admin access"
```

---

## Task 11: KioskActivity — pantalla principal

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/KioskActivity.kt`

- [ ] **Step 1: Crear `KioskActivity.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/KioskActivity.kt
package com.freekiosk.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.freekiosk.app.databinding.ActivityKioskBinding
import kotlinx.coroutines.*
import java.io.File

class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private var slideshow = SlideshowManager<File>(emptyList())
    private var slideshowJob: Job? = null
    private var currentRenderer: PdfPageRenderer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()
        startServer()
        setupLongPressAdmin()
        setupTapToAdvance()
    }

    override fun onResume() {
        super.onResume()
        reloadAndStart()
    }

    override fun onDestroy() {
        slideshowJob?.cancel()
        currentRenderer?.close()
        scope.cancel()
        super.onDestroy()
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private fun hideSystemUI() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ── Servidor ────────────────────────────────────────────────────────────

    private fun startServer() {
        startForegroundService(Intent(this, ServerService::class.java))
    }

    // ── Slideshow ───────────────────────────────────────────────────────────

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

        scope.launch(Dispatchers.IO) {
            val w = binding.pdfImageView.width.takeIf { it > 0 } ?: 1080
            val h = binding.pdfImageView.height.takeIf { it > 0 } ?: 1920
            try {
                currentRenderer?.close()
                val renderer = PdfPageRenderer(file)
                currentRenderer = renderer
                val bitmap = renderer.renderPage(0, w, h)
                withContext(Dispatchers.Main) {
                    binding.pdfImageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                // Archivo corrupto: avanzar al siguiente
                withContext(Dispatchers.Main) { advance() }
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

    // ── Navegación táctil ───────────────────────────────────────────────────

    private fun setupTapToAdvance() {
        binding.pdfImageView.setOnClickListener {
            slideshowJob?.cancel()
            advance()
            startAutoAdvance()
        }
    }

    // ── Acceso admin ────────────────────────────────────────────────────────

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

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/KioskActivity.kt
git commit -m "feat: KioskActivity fullscreen PDF slideshow with admin trigger"
```

---

## Task 12: AdminActivity — panel de gestión

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/AdminActivity.kt`

- [ ] **Step 1: Crear `AdminActivity.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/AdminActivity.kt
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
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshFileCount()
        setupServerUrl()
    }

    // ── Servidor ────────────────────────────────────────────────────────────

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

    // ── Archivos ────────────────────────────────────────────────────────────

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

    // ── Slideshow ───────────────────────────────────────────────────────────

    private fun setupSlider() {
        val current = Prefs.getIntervalSeconds(this).toFloat()
        binding.intervalSlider.value = current.coerceIn(3f, 60f)
        binding.intervalLabel.text = "${current.toInt()}s"
        binding.intervalSlider.addOnChangeListener { _, value, _ ->
            binding.intervalLabel.text = "${value.toInt()}s"
            Prefs.setIntervalSeconds(this, value.toLong())
        }
    }

    // ── Botones ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.importUsbBtn.setOnClickListener {
            pickPdf.launch("application/pdf")
        }
        binding.backToKioskBtn.setOnClickListener {
            finish()
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/AdminActivity.kt
git commit -m "feat: AdminActivity with file import, server URL and interval config"
```

---

## Task 13: MainActivity — entry point

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/MainActivity.kt`

- [ ] **Step 1: Crear `MainActivity.kt`**

```kotlin
// app/src/main/java/com/freekiosk/app/MainActivity.kt
package com.freekiosk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        } else {
            goToKiosk()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        goToKiosk()
    }

    private fun goToKiosk() {
        startActivity(Intent(this, KioskActivity::class.java))
        finish()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/MainActivity.kt
git commit -m "feat: MainActivity handles runtime permissions then launches kiosk"
```

---

## Task 14: AndroidManifest.xml

**Files:**
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Crear `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permisos -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.NoActionBar"
        android:hardwareAccelerated="true">

        <!-- Entry point: pide permisos y redirige al kiosk -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Kiosk fullscreen -->
        <activity
            android:name=".KioskActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:launchMode="singleTask"
            android:showOnLockScreen="true" />

        <!-- Panel admin -->
        <activity
            android:name=".AdminActivity"
            android:exported="false"
            android:screenOrientation="landscape" />

        <!-- Servicio WiFi foreground -->
        <service
            android:name=".ServerService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <!-- Auto-start al boot -->
        <receiver
            android:name=".BootReceiver"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

- [ ] **Step 2: Correr build de verificación**

```bash
./gradlew assembleDebug
```
Esperado: BUILD SUCCESSFUL. Si hay errores de recursos faltantes (ic_launcher), crear placeholder:
```bash
# Copiar cualquier PNG de 48x48 como ic_launcher en res/mipmap-mdpi/
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: AndroidManifest with all permissions, activities and receivers"
```

---

## Task 15: Build final e instalación en tablet

- [ ] **Step 1: Build APK debug**

```bash
./gradlew assembleDebug
```
APK generado en: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Instalar en tablet via ADB**

Con la tablet conectada por USB y depuración USB activada:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Verificar arranque**

```bash
adb shell am start -n com.freekiosk.app/.MainActivity
```
Esperado: La app pide permisos y entra al kiosk.

- [ ] **Step 4: Subir un PDF de prueba por WiFi**

1. Anotar la IP que aparece en AdminActivity
2. Desde PC en la misma red: abrir `http://IP:8080` en el browser
3. Subir un PDF de prueba
4. Verificar que aparece en el kiosk

- [ ] **Step 5: Verificar arranque automático**

```bash
adb shell reboot
```
Esperado: al reiniciar, la app arranca sola sin tocar nada.

- [ ] **Step 6: Commit final**

```bash
git add .
git commit -m "chore: final build verification"
```

---

## Resumen de permisos necesarios en la tablet

| Permiso | Por qué |
|---------|---------|
| Depuración USB | Solo para instalar el APK |
| Instalar apps desconocidas | Si se instala sin Google Play |
| Optimización de batería — desactivar | Para que el foreground service no sea matado |
