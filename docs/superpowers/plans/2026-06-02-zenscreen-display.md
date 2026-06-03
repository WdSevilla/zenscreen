# zenscreen-display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Android app for commercial displays/screens that shows scheduled media playlists — different content at different times of day — without any kiosk lockdown.

**Architecture:** New project at `/Users/wil/Documents/vibe-projects/zenscreen-display/`. Core media rendering (PDF/image/video) is copied from zenscreen. The key new layer is `ScheduleManager`: given the current time and day of week, it returns which files to play. `DisplayActivity` polls the schedule every 60 seconds and reloads if the active slot changed. `WifiServer` exposes CRUD endpoints for schedule slots plus the existing file management.

**Tech Stack:** Kotlin, Android SDK 26+, ExoPlayer (Media3 1.3.0), NanoHTTPD 2.3.1, Kotlinx Coroutines 1.7.3, Material Components, ViewBinding, org.json (built-in Android), JUnit 4 + Mockito for tests.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `settings.gradle.kts` | Create | Project name |
| `build.gradle.kts` | Create | Root build config |
| `gradle.properties` | Create | JVM + Android flags |
| `app/build.gradle.kts` | Create | App dependencies, versionName |
| `app/src/main/AndroidManifest.xml` | Create | Permissions, activities, receivers, services |
| `app/src/main/res/values/strings.xml` | Create | App name |
| `app/src/main/res/values/themes.xml` | Create | Fullscreen dark theme |
| `app/src/main/res/layout/activity_display.xml` | Create | ImageView + PlayerView + empty state text |
| `app/src/main/java/com/zenscreen/display/MediaType.kt` | Create | Enum: PDF, IMAGE, VIDEO |
| `app/src/main/java/com/zenscreen/display/MediaFile.kt` | Create | data class(file, type) |
| `app/src/main/java/com/zenscreen/display/FileManager.kt` | Create | List/save/delete media files, listMediaOrdered |
| `app/src/main/java/com/zenscreen/display/SlideshowManager.kt` | Create | Circular index, next/prev/seekTo/reload |
| `app/src/main/java/com/zenscreen/display/PdfPageRenderer.kt` | Create | Android PdfRenderer wrapper |
| `app/src/main/java/com/zenscreen/display/ScheduleSlot.kt` | Create | Data model + JSON serialization |
| `app/src/main/java/com/zenscreen/display/ScheduleManager.kt` | Create | Resolve active slot from current time+day |
| `app/src/main/java/com/zenscreen/display/Prefs.kt` | Create | SharedPreferences: interval, orientation, slots, playlist order |
| `app/src/main/java/com/zenscreen/display/WifiServer.kt` | Create | NanoHTTPD: file upload/delete/reorder + schedule CRUD |
| `app/src/main/java/com/zenscreen/display/ServerService.kt` | Create | Foreground service keeping WifiServer alive |
| `app/src/main/java/com/zenscreen/display/BootReceiver.kt` | Create | Start ServerService on BOOT_COMPLETED |
| `app/src/main/java/com/zenscreen/display/DisplayActivity.kt` | Create | Media player + schedule polling, no kiosk lock |
| `app/src/test/java/com/zenscreen/display/ScheduleManagerTest.kt` | Create | Unit tests for slot resolution logic |
| `app/src/test/java/com/zenscreen/display/SlideshowManagerTest.kt` | Create | Unit tests for circular navigation |

---

## Task 1: Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`

- [ ] **Step 1: Create project root directory**

```bash
mkdir -p /Users/wil/Documents/vibe-projects/zenscreen-display/app/src/main/java/com/zenscreen/display
mkdir -p /Users/wil/Documents/vibe-projects/zenscreen-display/app/src/main/res/layout
mkdir -p /Users/wil/Documents/vibe-projects/zenscreen-display/app/src/main/res/values
mkdir -p /Users/wil/Documents/vibe-projects/zenscreen-display/app/src/test/java/com/zenscreen/display
```

- [ ] **Step 2: Create `settings.gradle.kts`**

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
rootProject.name = "zenscreen-display"
include(":app")
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
```

- [ ] **Step 5: Copy Gradle wrapper from zenscreen**

```bash
cp -r /Users/wil/Documents/vibe-projects/zenscreen/gradle \
      /Users/wil/Documents/vibe-projects/zenscreen-display/
cp /Users/wil/Documents/vibe-projects/zenscreen/gradlew \
   /Users/wil/Documents/vibe-projects/zenscreen-display/
cp /Users/wil/Documents/vibe-projects/zenscreen/gradlew.bat \
   /Users/wil/Documents/vibe-projects/zenscreen-display/
chmod +x /Users/wil/Documents/vibe-projects/zenscreen-display/gradlew
```

- [ ] **Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zenscreen.display"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zenscreen.display"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

- [ ] **Step 7: Verify project syncs**

```bash
cd /Users/wil/Documents/vibe-projects/zenscreen-display
./gradlew tasks --quiet 2>&1 | head -20
```

Expected: task list prints without errors.

- [ ] **Step 8: Commit**

```bash
cd /Users/wil/Documents/vibe-projects/zenscreen-display
git init && git add .
git commit -m "chore: scaffold zenscreen-display project"
```

---

## Task 2: Core Media Classes

**Files:**
- Create: `app/src/main/java/com/zenscreen/display/MediaType.kt`
- Create: `app/src/main/java/com/zenscreen/display/MediaFile.kt`
- Create: `app/src/main/java/com/zenscreen/display/FileManager.kt`
- Create: `app/src/main/java/com/zenscreen/display/SlideshowManager.kt`
- Create: `app/src/main/java/com/zenscreen/display/PdfPageRenderer.kt`
- Create: `app/src/test/java/com/zenscreen/display/SlideshowManagerTest.kt`

- [ ] **Step 1: Create `MediaType.kt`**

```kotlin
package com.zenscreen.display

enum class MediaType { PDF, IMAGE, VIDEO }
```

- [ ] **Step 2: Create `MediaFile.kt`**

```kotlin
package com.zenscreen.display

import java.io.File

data class MediaFile(val file: File, val type: MediaType)
```

- [ ] **Step 3: Create `FileManager.kt`**

```kotlin
package com.zenscreen.display

import android.content.Context
import java.io.File

object FileManager {

    private val PDF_EXTS   = setOf("pdf")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")

    fun getMediaDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "media")
        dir.mkdirs()
        return dir
    }

    fun mediaTypeOf(file: File): MediaType? = when (file.extension.lowercase()) {
        in PDF_EXTS   -> MediaType.PDF
        in IMAGE_EXTS -> MediaType.IMAGE
        in VIDEO_EXTS -> MediaType.VIDEO
        else          -> null
    }

    fun listMedia(dir: File): List<MediaFile> =
        dir.listFiles()
            ?.mapNotNull { f -> mediaTypeOf(f)?.let { MediaFile(f, it) } }
            ?.sortedBy { it.file.name }
            ?: emptyList()

    fun listMediaOrdered(dir: File, order: List<String>): List<MediaFile> {
        val all = listMedia(dir)
        val byName = all.associateBy { it.file.name }
        val ordered = order.mapNotNull { byName[it] }
        val new = all.filter { it.file.name !in order }
        return ordered + new
    }

    fun saveMedia(dir: File, name: String, bytes: ByteArray) {
        val safe = File(name).name
        File(dir, safe).writeBytes(bytes)
    }

    fun deleteFile(file: File) { file.delete() }
}
```

- [ ] **Step 4: Create `SlideshowManager.kt`**

```kotlin
package com.zenscreen.display

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

    fun seekTo(idx: Int) {
        if (idx in list.indices) index = idx
    }
}
```

- [ ] **Step 5: Create `PdfPageRenderer.kt`**

```kotlin
package com.zenscreen.display

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfPageRenderer(file: File) : AutoCloseable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)

    fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap {
        val page = renderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    override fun close() {
        renderer.close()
        fd.close()
    }
}
```

- [ ] **Step 6: Write `SlideshowManagerTest.kt`**

```kotlin
package com.zenscreen.display

import org.junit.Assert.*
import org.junit.Test

class SlideshowManagerTest {

    @Test fun `next wraps around`() {
        val sm = SlideshowManager(listOf("a", "b", "c"))
        sm.next(); sm.next(); sm.next()
        assertEquals("a", sm.current())
    }

    @Test fun `prev wraps around`() {
        val sm = SlideshowManager(listOf("a", "b", "c"))
        sm.prev()
        assertEquals("c", sm.current())
    }

    @Test fun `seekTo sets correct index`() {
        val sm = SlideshowManager(listOf("a", "b", "c"))
        sm.seekTo(2)
        assertEquals("c", sm.current())
    }

    @Test fun `reload resets index to 0`() {
        val sm = SlideshowManager(listOf("a", "b"))
        sm.next()
        sm.reload(listOf("x", "y", "z"))
        assertEquals("x", sm.current())
    }

    @Test fun `isEmpty returns true for empty list`() {
        assertTrue(SlideshowManager<String>(emptyList()).isEmpty())
    }
}
```

- [ ] **Step 7: Run tests**

```bash
cd /Users/wil/Documents/vibe-projects/zenscreen-display
./gradlew test --tests "com.zenscreen.display.SlideshowManagerTest" 2>&1 | tail -10
```

Expected: `5 tests completed, 0 failed`

- [ ] **Step 8: Commit**

```bash
git add app/src/
git commit -m "feat: add core media classes and SlideshowManager tests"
```

---

## Task 3: ScheduleSlot + ScheduleManager

**Files:**
- Create: `app/src/main/java/com/zenscreen/display/ScheduleSlot.kt`
- Create: `app/src/main/java/com/zenscreen/display/ScheduleManager.kt`
- Create: `app/src/test/java/com/zenscreen/display/ScheduleManagerTest.kt`

- [ ] **Step 1: Write failing tests first**

```kotlin
// app/src/test/java/com/zenscreen/display/ScheduleManagerTest.kt
package com.zenscreen.display

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleManagerTest {

    private fun slot(
        start: Int, end: Int,
        days: Set<Int> = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                               Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY),
        files: List<String> = listOf("a.mp4"),
        enabled: Boolean = true,
        id: String = "s1"
    ) = ScheduleSlot(id=id, name="Test", startMinute=start, endMinute=end,
                     days=days, files=files, enabled=enabled, intervalSeconds=10)

    @Test fun `active slot matches current time and day`() {
        val s = slot(start = 9*60, end = 11*60, days = setOf(Calendar.MONDAY))
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 10*60, dayOfWeek = Calendar.MONDAY)
        assertEquals(s, result)
    }

    @Test fun `no slot outside time window`() {
        val s = slot(start = 9*60, end = 11*60)
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 8*60, dayOfWeek = Calendar.MONDAY)
        assertNull(result)
    }

    @Test fun `no slot on wrong day`() {
        val s = slot(start = 9*60, end = 11*60, days = setOf(Calendar.SATURDAY))
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 10*60, dayOfWeek = Calendar.MONDAY)
        assertNull(result)
    }

    @Test fun `disabled slot is ignored`() {
        val s = slot(start = 9*60, end = 11*60, enabled = false)
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 10*60, dayOfWeek = Calendar.MONDAY)
        assertNull(result)
    }

    @Test fun `first matching slot wins when overlapping`() {
        val s1 = slot(start = 9*60, end = 12*60, id = "s1")
        val s2 = slot(start = 10*60, end = 13*60, id = "s2")
        val result = ScheduleManager.activeSlot(listOf(s1, s2), nowMinute = 11*60, dayOfWeek = Calendar.MONDAY)
        assertEquals("s1", result?.id)
    }

    @Test fun `slot at exact start minute is active`() {
        val s = slot(start = 9*60, end = 11*60)
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 9*60, dayOfWeek = Calendar.MONDAY)
        assertNotNull(result)
    }

    @Test fun `slot at exact end minute is not active`() {
        val s = slot(start = 9*60, end = 11*60)
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 11*60, dayOfWeek = Calendar.MONDAY)
        assertNull(result)
    }

    @Test fun `slot spanning midnight matches before midnight`() {
        val s = slot(start = 22*60, end = 2*60)  // 22:00 → 02:00
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 23*60, dayOfWeek = Calendar.MONDAY)
        assertNotNull(result)
    }

    @Test fun `slot spanning midnight matches after midnight`() {
        val s = slot(start = 22*60, end = 2*60)
        val result = ScheduleManager.activeSlot(listOf(s), nowMinute = 1*60, dayOfWeek = Calendar.TUESDAY)
        assertNotNull(result)
    }

    @Test fun `ScheduleSlot serializes and deserializes from JSON`() {
        val original = slot(start = 9*60, end = 17*60, days = setOf(Calendar.MONDAY, Calendar.FRIDAY))
        val json = original.toJson()
        val restored = ScheduleSlot.fromJson(json)
        assertEquals(original, restored)
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd /Users/wil/Documents/vibe-projects/zenscreen-display
./gradlew test --tests "com.zenscreen.display.ScheduleManagerTest" 2>&1 | tail -5
```

Expected: compilation error (ScheduleSlot/ScheduleManager not found).

- [ ] **Step 3: Create `ScheduleSlot.kt`**

```kotlin
package com.zenscreen.display

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents one scheduled time window.
 * Time is stored as minutes from midnight (0–1439).
 * When startMinute > endMinute the slot spans midnight (e.g. 22:00→02:00).
 */
data class ScheduleSlot(
    val id: String,
    val name: String,
    val startMinute: Int,       // 0–1439
    val endMinute: Int,         // 0–1439
    val days: Set<Int>,         // Calendar.MONDAY(2)..Calendar.SUNDAY(1)
    val files: List<String>,    // filenames in media dir
    val enabled: Boolean,
    val intervalSeconds: Long   // for images/PDFs in this slot
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startMinute", startMinute)
        put("endMinute", endMinute)
        put("days", JSONArray(days.toList()))
        put("files", JSONArray(files))
        put("enabled", enabled)
        put("intervalSeconds", intervalSeconds)
    }

    companion object {
        fun fromJson(json: JSONObject): ScheduleSlot {
            val daysArr = json.getJSONArray("days")
            val filesArr = json.getJSONArray("files")
            return ScheduleSlot(
                id = json.getString("id"),
                name = json.getString("name"),
                startMinute = json.getInt("startMinute"),
                endMinute = json.getInt("endMinute"),
                days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet(),
                files = (0 until filesArr.length()).map { filesArr.getString(it) },
                enabled = json.getBoolean("enabled"),
                intervalSeconds = json.getLong("intervalSeconds")
            )
        }
    }
}
```

- [ ] **Step 4: Create `ScheduleManager.kt`**

```kotlin
package com.zenscreen.display

import java.util.Calendar

object ScheduleManager {

    /**
     * Returns the first enabled slot whose time window and day match.
     * Supports slots spanning midnight (startMinute > endMinute).
     */
    fun activeSlot(
        slots: List<ScheduleSlot>,
        nowMinute: Int,
        dayOfWeek: Int
    ): ScheduleSlot? = slots.firstOrNull { slot ->
        if (!slot.enabled) return@firstOrNull false
        isActive(slot, nowMinute, dayOfWeek)
    }

    private fun isActive(slot: ScheduleSlot, nowMinute: Int, dayOfWeek: Int): Boolean {
        return if (slot.startMinute <= slot.endMinute) {
            // Normal window (e.g. 09:00–17:00)
            dayOfWeek in slot.days &&
            nowMinute >= slot.startMinute &&
            nowMinute < slot.endMinute
        } else {
            // Midnight-spanning window (e.g. 22:00–02:00)
            // Active on the START day after startMinute, OR on the next day before endMinute
            val prevDay = if (dayOfWeek == Calendar.SUNDAY) Calendar.SATURDAY
                          else dayOfWeek - 1
            (dayOfWeek in slot.days && nowMinute >= slot.startMinute) ||
            (prevDay in slot.days && nowMinute < slot.endMinute)
        }
    }

    /** Current time as minutes from midnight using system clock. */
    fun nowMinute(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    /** Today's day of week as Calendar constant (Calendar.MONDAY etc). */
    fun todayDayOfWeek(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
}
```

- [ ] **Step 5: Run tests — all must pass**

```bash
cd /Users/wil/Documents/vibe-projects/zenscreen-display
./gradlew test --tests "com.zenscreen.display.ScheduleManagerTest" 2>&1 | tail -10
```

Expected: `10 tests completed, 0 failed`

- [ ] **Step 6: Commit**

```bash
git add app/src/
git commit -m "feat: add ScheduleSlot and ScheduleManager with full test coverage"
```

---

## Task 4: Prefs

**Files:**
- Create: `app/src/main/java/com/zenscreen/display/Prefs.kt`

- [ ] **Step 1: Create `Prefs.kt`**

```kotlin
package com.zenscreen.display

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object Prefs {
    const val DEFAULT_INTERVAL_SECONDS = 10L
    const val DEFAULT_ORIENTATION = "landscape"
    const val DEFAULT_SERVER_PORT = 8181

    private const val NAME = "display_prefs"
    private const val KEY_INTERVAL = "interval"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_PORT = "server_port"
    private const val KEY_PLAYLIST = "playlist_order"
    private const val KEY_SCHEDULE = "schedule_slots"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ── Interval ──────────────────────────────────────────────────────────────
    fun getIntervalSeconds(ctx: Context): Long =
        prefs(ctx).getLong(KEY_INTERVAL, DEFAULT_INTERVAL_SECONDS)
    fun setIntervalSeconds(ctx: Context, v: Long) =
        prefs(ctx).edit().putLong(KEY_INTERVAL, v).apply()

    // ── Orientation ───────────────────────────────────────────────────────────
    fun getOrientation(ctx: Context): String =
        prefs(ctx).getString(KEY_ORIENTATION, DEFAULT_ORIENTATION) ?: DEFAULT_ORIENTATION
    fun setOrientation(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_ORIENTATION, v).apply()

    // ── Server port ───────────────────────────────────────────────────────────
    fun getServerPort(ctx: Context): Int =
        prefs(ctx).getInt(KEY_PORT, DEFAULT_SERVER_PORT)
    fun setServerPort(ctx: Context, port: Int) =
        prefs(ctx).edit().putInt(KEY_PORT, port).apply()

    // ── Playlist order ────────────────────────────────────────────────────────
    fun getPlaylistOrder(ctx: Context): List<String> {
        val s = prefs(ctx).getString(KEY_PLAYLIST, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split("\n")
    }
    fun setPlaylistOrder(ctx: Context, order: List<String>) =
        prefs(ctx).edit().putString(KEY_PLAYLIST, order.joinToString("\n")).apply()

    // ── Schedule slots ────────────────────────────────────────────────────────
    fun getScheduleSlots(ctx: Context): List<ScheduleSlot> {
        val json = prefs(ctx).getString(KEY_SCHEDULE, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ScheduleSlot.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun setScheduleSlots(ctx: Context, slots: List<ScheduleSlot>) {
        val arr = JSONArray()
        slots.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_SCHEDULE, arr.toString()).apply()
    }

    fun addOrUpdateSlot(ctx: Context, slot: ScheduleSlot) {
        val slots = getScheduleSlots(ctx).toMutableList()
        val idx = slots.indexOfFirst { it.id == slot.id }
        if (idx >= 0) slots[idx] = slot else slots.add(slot)
        setScheduleSlots(ctx, slots)
    }

    fun deleteSlot(ctx: Context, id: String) {
        val slots = getScheduleSlots(ctx).filter { it.id != id }
        setScheduleSlots(ctx, slots)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/zenscreen/display/Prefs.kt
git commit -m "feat: add Prefs with schedule slot persistence"
```

---

## Task 5: Resource Files + Manifest

**Files:**
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/layout/activity_display.xml`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `strings.xml`**

```xml
<resources>
    <string name="app_name">ZenScreen Display</string>
    <string name="no_media">Sin contenido. Sube archivos vía WiFi.</string>
</resources>
```

- [ ] **Step 2: Create `themes.xml`**

```xml
<resources>
    <style name="Theme.ZenDisplay" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/black</item>
        <item name="android:navigationBarColor">@android:color/black</item>
    </style>
</resources>
```

- [ ] **Step 3: Create `activity_display.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <ImageView
        android:id="@+id/imageView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitCenter"
        android:contentDescription="Media" />

    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        app:use_controller="false"
        xmlns:app="http://schemas.android.com/apk/res-auto" />

    <TextView
        android:id="@+id/emptyText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/no_media"
        android:textColor="#64748b"
        android:textSize="18sp"
        android:gravity="center"
        android:padding="32dp"
        android:visibility="gone" />

</FrameLayout>
```

- [ ] **Step 4: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:theme="@style/Theme.ZenDisplay"
        android:hardwareAccelerated="true">

        <activity
            android:name=".DisplayActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".ServerService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />

        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter android:priority="1000">
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/
git commit -m "feat: add resource files and AndroidManifest"
```

---

## Task 6: ServerService + BootReceiver

**Files:**
- Create: `app/src/main/java/com/zenscreen/display/ServerService.kt`
- Create: `app/src/main/java/com/zenscreen/display/BootReceiver.kt`

- [ ] **Step 1: Create `ServerService.kt`**

```kotlin
package com.zenscreen.display

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ServerService : Service() {

    private var server: WifiServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        val port = Prefs.getServerPort(this)
        val dir  = FileManager.getMediaDir(this)
        server?.stop()
        server = WifiServer(port, dir, this).also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "display_server"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Servidor WiFi",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        val port = Prefs.getServerPort(this)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ZenScreen Display")
            .setContentText("Servidor activo en puerto $port")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}
```

- [ ] **Step 2: Create `BootReceiver.kt`**

```kotlin
package com.zenscreen.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, ServerService::class.java))
            context.startActivity(
                Intent(context, DisplayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zenscreen/display/ServerService.kt \
        app/src/main/java/com/zenscreen/display/BootReceiver.kt
git commit -m "feat: add ServerService and BootReceiver"
```

---

## Task 7: WifiServer (File Management + Schedule API)

**Files:**
- Create: `app/src/main/java/com/zenscreen/display/WifiServer.kt`

The server exposes:
- `GET /` — HTML dashboard with file management + schedule UI
- `GET /api/files` — file list in playlist order (JSON)
- `POST /upload` — upload file (multipart)
- `POST /delete` — delete one file (plain text body)
- `POST /delete-bulk` — delete multiple (JSON array)
- `POST /reorder` — save playlist order (JSON array)
- `GET /api/schedule` — all slots (JSON)
- `POST /api/schedule` — create slot (JSON body)
- `PUT /api/schedule` — update slot (JSON body, slot must have existing id)
- `DELETE /api/schedule` — delete slot (plain text body = slot id)
- `GET /api/schedule/active` — currently active slot or null (JSON)

- [ ] **Step 1: Create `WifiServer.kt`**

```kotlin
package com.zenscreen.display

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.Calendar
import java.util.UUID

class WifiServer(port: Int, private val mediaDir: File, private val ctx: Context) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.GET  && session.uri == "/"                  -> serveDashboard()
        session.method == Method.POST && session.uri == "/upload"            -> handleUpload(session)
        session.method == Method.POST && session.uri == "/delete"            -> handleDelete(session)
        session.method == Method.POST && session.uri == "/delete-bulk"       -> handleDeleteBulk(session)
        session.method == Method.POST && session.uri == "/reorder"           -> handleReorder(session)
        session.method == Method.GET  && session.uri == "/api/files"         -> serveFileList()
        session.method == Method.GET  && session.uri == "/api/schedule"      -> serveSchedule()
        session.method == Method.GET  && session.uri == "/api/schedule/active" -> serveActiveSlot()
        session.method == Method.POST && session.uri == "/api/schedule"      -> handleCreateSlot(session)
        session.method == Method.PUT  && session.uri == "/api/schedule"      -> handleUpdateSlot(session)
        session.method == Method.DELETE && session.uri == "/api/schedule"    -> handleDeleteSlot(session)
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    private fun serveFileList(): Response {
        val order = Prefs.getPlaylistOrder(ctx)
        val files = FileManager.listMediaOrdered(mediaDir, order)
        val json = buildString {
            append("""{"files":[""")
            files.forEachIndexed { i, mf ->
                if (i > 0) append(",")
                val icon = when (mf.type) {
                    MediaType.PDF   -> "doc"
                    MediaType.IMAGE -> "img"
                    MediaType.VIDEO -> "vid"
                }
                append("""{"name":"${mf.file.name}","size":${mf.file.length()},"type":"$icon"}""")
            }
            append("]}")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleUpload(session: IHTTPSession): Response = try {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val tmpPath = files["file"] ?: return badRequest("No file")
        val filename = session.parameters["file"]?.firstOrNull() ?: "upload.bin"
        val safeName = File(filename).name
        if (FileManager.mediaTypeOf(File(safeName)) == null)
            return badRequest("Formato no soportado")
        File(tmpPath).copyTo(File(mediaDir, safeName), overwrite = true)
        ok("OK")
    } catch (e: Exception) { serverError(e.message) }

    private fun handleDelete(session: IHTTPSession): Response = try {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val name = File(body["postData"] ?: return badRequest("No name")).name
        File(mediaDir, name).delete()
        Prefs.setPlaylistOrder(ctx, Prefs.getPlaylistOrder(ctx).filter { it != name })
        ok("OK")
    } catch (e: Exception) { serverError(e.message) }

    private fun handleDeleteBulk(session: IHTTPSession): Response = try {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val names = parseJsonStringArray(body["postData"] ?: return badRequest("No body"))
        names.forEach { File(mediaDir, File(it).name).delete() }
        Prefs.setPlaylistOrder(ctx, Prefs.getPlaylistOrder(ctx).filter { it !in names })
        ok("OK")
    } catch (e: Exception) { serverError(e.message) }

    private fun handleReorder(session: IHTTPSession): Response = try {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val order = parseJsonStringArray(body["postData"] ?: return badRequest("No body"))
        Prefs.setPlaylistOrder(ctx, order)
        ok("OK")
    } catch (e: Exception) { serverError(e.message) }

    // ── Schedule ──────────────────────────────────────────────────────────────

    private fun serveSchedule(): Response {
        val slots = Prefs.getScheduleSlots(ctx)
        val arr = org.json.JSONArray()
        slots.forEach { arr.put(it.toJson()) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", arr.toString())
    }

    private fun serveActiveSlot(): Response {
        val slots = Prefs.getScheduleSlots(ctx)
        val active = ScheduleManager.activeSlot(slots, ScheduleManager.nowMinute(), ScheduleManager.todayDayOfWeek())
        val json = active?.toJson()?.toString() ?: "null"
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleCreateSlot(session: IHTTPSession): Response = try {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val json = org.json.JSONObject(body["postData"] ?: return badRequest("No body"))
        // Assign a new UUID if not provided
        if (!json.has("id") || json.getString("id").isBlank())
            json.put("id", UUID.randomUUID().toString())
        val slot = ScheduleSlot.fromJson(json)
        Prefs.addOrUpdateSlot(ctx, slot)
        newFixedLengthResponse(Response.Status.CREATED, "application/json", slot.toJson().toString())
    } catch (e: Exception) { serverError(e.message) }

    private fun handleUpdateSlot(session: IHTTPSession): Response = try {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val slot = ScheduleSlot.fromJson(org.json.JSONObject(body["postData"] ?: return badRequest("No body")))
        Prefs.addOrUpdateSlot(ctx, slot)
        ok("OK")
    } catch (e: Exception) { serverError(e.message) }

    private fun handleDeleteSlot(session: IHTTPSession): Response = try {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val id = body["postData"] ?: return badRequest("No id")
        Prefs.deleteSlot(ctx, id.trim())
        ok("OK")
    } catch (e: Exception) { serverError(e.message) }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseJsonStringArray(json: String): List<String> {
        val arr = org.json.JSONArray(json.trim())
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun ok(body: String) = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, body)
    private fun badRequest(msg: String) = newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, msg)
    private fun serverError(msg: String?) = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, msg ?: "Error")

    // ── Dashboard HTML ────────────────────────────────────────────────────────

    private fun serveDashboard(): Response {
        val port = Prefs.getServerPort(ctx)
        val html = buildDashboardHtml(port)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun buildDashboardHtml(port: Int): String = """<!DOCTYPE html>
<html lang="es"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ZenScreen Display</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:system-ui,sans-serif;background:#080a10;color:#e2e8f0;padding:24px;max-width:900px;margin:0 auto}
h1{font-size:18px;font-weight:800;color:#4f8ef7;margin-bottom:24px;letter-spacing:-.02em}
.card{background:#111420;border:1px solid rgba(255,255,255,.08);border-radius:12px;padding:20px;margin-bottom:20px}
h2{font-size:11px;color:#5a6585;text-transform:uppercase;letter-spacing:.08em;margin-bottom:14px}
.drop{border:2px dashed rgba(255,255,255,.08);border-radius:10px;padding:28px;text-align:center;cursor:pointer;position:relative}
.drop input{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%}
.drop:hover{border-color:#4f8ef7}
.drop p{color:#5a6585;font-size:13px}
.file-row{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.file-row:last-child{border:none}
.drag-handle{cursor:grab;color:#2a2d40;font-size:18px;user-select:none;flex-shrink:0}
.chk{width:15px;height:15px;accent-color:#4f8ef7;flex-shrink:0;cursor:pointer}
.file-name{flex:1;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.file-type{font-size:10px;color:#5a6585;background:#1a1d27;padding:2px 7px;border-radius:4px;flex-shrink:0}
.btn{border:none;border-radius:8px;padding:6px 14px;cursor:pointer;font-size:12px;font-weight:600}
.btn-del{background:rgba(239,68,68,.15);color:#ef4444}
.btn-del:hover{opacity:.8}
.btn-bulk{background:rgba(239,68,68,.2);color:#ef4444;border:1px solid rgba(239,68,68,.3);display:none;margin-bottom:10px}
.btn-selall{background:#1a1d27;color:#94a3b8;border:1px solid rgba(255,255,255,.08);margin-bottom:10px;margin-right:8px}
.hint{font-size:11px;color:#2a2d40;margin-top:8px}
.active-now{background:rgba(79,142,247,.1);border:1px solid rgba(79,142,247,.25);border-radius:8px;padding:10px 14px;font-size:13px;color:#93bbfd;margin-bottom:14px}
/* Schedule */
.slot-row{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.slot-row:last-child{border:none}
.slot-name{flex:1;font-size:13px;font-weight:600}
.slot-time{font-size:12px;color:#5a6585;font-family:monospace}
.slot-days{font-size:11px;color:#5a6585}
.slot-files{font-size:11px;color:#4f8ef7}
.btn-edit{background:rgba(79,142,247,.15);color:#4f8ef7;border:1px solid rgba(79,142,247,.2)}
.btn-edit:hover{opacity:.8}
.tag-on{display:inline-block;background:rgba(34,197,94,.15);color:#4ade80;border-radius:4px;padding:1px 7px;font-size:10px;font-weight:700;margin-right:6px}
.tag-off{display:inline-block;background:rgba(100,116,139,.15);color:#5a6585;border-radius:4px;padding:1px 7px;font-size:10px;font-weight:700;margin-right:6px}
/* Modal */
.overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.7);z-index:100;align-items:center;justify-content:center}
.overlay.open{display:flex}
.modal{background:#111420;border:1px solid rgba(255,255,255,.1);border-radius:16px;padding:28px;width:100%;max-width:500px;max-height:90vh;overflow-y:auto}
.modal h3{font-size:16px;font-weight:800;margin-bottom:20px}
.form-group{margin-bottom:16px}
.form-group label{display:block;font-size:11px;color:#5a6585;text-transform:uppercase;letter-spacing:.06em;margin-bottom:6px}
.form-group input,.form-group select{width:100%;background:#080a10;border:1px solid rgba(255,255,255,.1);border-radius:8px;padding:9px 12px;color:#e2e8f0;font-size:14px;outline:none}
.form-group input:focus,.form-group select:focus{border-color:#4f8ef7}
.day-grid{display:flex;gap:6px;flex-wrap:wrap}
.day-btn{background:#1a1d27;border:1px solid rgba(255,255,255,.08);border-radius:8px;padding:5px 10px;font-size:12px;font-weight:600;color:#5a6585;cursor:pointer;transition:.15s}
.day-btn.on{background:rgba(79,142,247,.2);border-color:rgba(79,142,247,.4);color:#4f8ef7}
.file-check-row{display:flex;align-items:center;gap:8px;padding:7px 0;border-bottom:1px solid rgba(255,255,255,.05)}
.file-check-row:last-child{border:none}
.file-check-row label{font-size:13px;cursor:pointer;flex:1}
.modal-footer{display:flex;gap:10px;justify-content:flex-end;margin-top:20px}
.btn-save{background:#4f8ef7;color:#fff;border:none;border-radius:8px;padding:9px 22px;font-size:13px;font-weight:700;cursor:pointer}
.btn-save:hover{background:#3a7be0}
.btn-cancel{background:rgba(255,255,255,.06);color:#94a3b8;border:none;border-radius:8px;padding:9px 18px;font-size:13px;font-weight:600;cursor:pointer}
.btn-cancel:hover{background:rgba(255,255,255,.1)}
.btn-add{background:rgba(79,142,247,.15);color:#4f8ef7;border:1px solid rgba(79,142,247,.2);border-radius:8px;padding:7px 16px;font-size:12px;font-weight:700;cursor:pointer;margin-top:4px}
.btn-add:hover{opacity:.8}
</style></head><body>
<h1>ZenScreen Display</h1>

<div class="card">
  <h2>Subir archivos</h2>
  <div class="drop">
    <input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.mp4,.mkv,.mov,.avi,.webm" multiple onchange="upload(this.files)">
    <p>Arrastra archivos aqui o toca para seleccionar<br><small style="font-size:10px">PDF · JPG · PNG · WebP · MP4 · MKV · MOV</small></p>
  </div>
</div>

<div class="card">
  <h2>Playlist por defecto (<span id="count">...</span>)</h2>
  <div id="activeNow"></div>
  <button class="btn btn-selall" onclick="toggleAll()">Seleccionar todo</button>
  <button class="btn btn-bulk" id="btnBulk" onclick="delSelected()">Borrar seleccionados (<span id="selCount">0</span>)</button>
  <div id="fileList">Cargando...</div>
  <p class="hint">Arrastra para reordenar el playlist por defecto</p>
</div>

<div class="card">
  <h2>Programacion de horarios</h2>
  <div id="scheduleList">Cargando...</div>
  <button class="btn btn-add" onclick="openSlotModal(null)">+ Nuevo horario</button>
</div>

<!-- SLOT MODAL -->
<div class="overlay" id="overlay">
  <div class="modal">
    <h3 id="modalTitle">Nuevo horario</h3>
    <input type="hidden" id="slotId">
    <div class="form-group">
      <label>Nombre</label>
      <input type="text" id="slotName" placeholder="Ej: Horario manana">
    </div>
    <div class="form-group">
      <label>Hora inicio</label>
      <input type="time" id="slotStart">
    </div>
    <div class="form-group">
      <label>Hora fin</label>
      <input type="time" id="slotEnd">
    </div>
    <div class="form-group">
      <label>Intervalo imagenes/PDF (segundos)</label>
      <input type="number" id="slotInterval" value="10" min="3" max="300">
    </div>
    <div class="form-group">
      <label>Dias</label>
      <div class="day-grid" id="dayGrid">
        <button class="day-btn" data-day="2">Lun</button>
        <button class="day-btn" data-day="3">Mar</button>
        <button class="day-btn" data-day="4">Mie</button>
        <button class="day-btn" data-day="5">Jue</button>
        <button class="day-btn" data-day="6">Vie</button>
        <button class="day-btn" data-day="7">Sab</button>
        <button class="day-btn" data-day="1">Dom</button>
      </div>
    </div>
    <div class="form-group">
      <label>Archivos de este horario</label>
      <div id="fileCheckList">Cargando...</div>
    </div>
    <div class="modal-footer">
      <button class="btn-cancel" onclick="closeModal()">Cancelar</button>
      <button class="btn-save" onclick="saveSlot()">Guardar</button>
    </div>
  </div>
</div>

<script>
let allFiles=[], allSlots=[], dragSrc=null, editingSlotFiles=[];

const DAY_NAMES={1:'Dom',2:'Lun',3:'Mar',4:'Mie',5:'Jue',6:'Vie',7:'Sab'};
function minToTime(m){const h=Math.floor(m/60).toString().padStart(2,'0');const mm=(m%60).toString().padStart(2,'0');return h+':'+mm;}
function timeToMin(t){const[h,m]=t.split(':').map(Number);return h*60+m;}

async function load(){
  const [fr, sr] = await Promise.all([fetch('/api/files'), fetch('/api/schedule')]);
  const fd=await fr.json(); allFiles=fd.files;
  allSlots=await sr.json();
  renderFiles(); renderSchedule();
  // Active slot
  const ar=await fetch('/api/schedule/active');
  const active=await ar.json();
  document.getElementById('activeNow').innerHTML = active
    ? '<div class="active-now">Horario activo ahora: <strong>'+esc(active.name)+'</strong> — '+minToTime(active.startMinute)+' a '+minToTime(active.endMinute)+'</div>'
    : '';
}

function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');}

function renderFiles(){
  document.getElementById('count').textContent=allFiles.length;
  if(!allFiles.length){document.getElementById('fileList').innerHTML='<p style="color:#5a6585;font-size:13px;padding:8px 0">Sin archivos</p>';return;}
  document.getElementById('fileList').innerHTML=allFiles.map(f=>`
    <div class="file-row" draggable="true" data-name="${esc(f.name)}">
      <span class="drag-handle">⠿</span>
      <input type="checkbox" class="chk" value="${esc(f.name)}" onchange="updateBulk()">
      <span class="file-name">${esc(f.name)}</span>
      <span class="file-type">${f.type}</span>
      <button class="btn btn-del" onclick="del('${esc(f.name)}')">Borrar</button>
    </div>`).join('');
  setupDrag();
}

function renderSchedule(){
  const el=document.getElementById('scheduleList');
  if(!allSlots.length){el.innerHTML='<p style="color:#5a6585;font-size:13px;padding:8px 0">Sin horarios configurados</p>';return;}
  el.innerHTML=allSlots.map(s=>`
    <div class="slot-row">
      <div>
        <span class="${s.enabled?'tag-on':'tag-off'}">${s.enabled?'ON':'OFF'}</span>
        <span class="slot-name">${esc(s.name)}</span><br>
        <span class="slot-time">${minToTime(s.startMinute)} — ${minToTime(s.endMinute)}</span>
        <span class="slot-days"> &nbsp;|&nbsp; ${s.days.map(d=>DAY_NAMES[d]).join(' ')}</span><br>
        <span class="slot-files">${s.files.length} archivo(s)</span>
      </div>
      <button class="btn btn-edit" onclick='openSlotModal(${JSON.stringify(s)})'>Editar</button>
      <button class="btn btn-del" onclick="delSlot('${esc(s.id)}')">Borrar</button>
    </div>`).join('');
}

// ── File drag & drop order ──
function setupDrag(){
  document.querySelectorAll('.file-row').forEach(row=>{
    row.addEventListener('dragstart',e=>{dragSrc=row;row.style.opacity='.4';e.dataTransfer.effectAllowed='move';});
    row.addEventListener('dragend',()=>{row.style.opacity='1';saveOrder();});
    row.addEventListener('dragover',e=>{e.preventDefault();if(dragSrc&&dragSrc!==row){const rows=[...document.querySelectorAll('.file-row')];const si=rows.indexOf(dragSrc),di=rows.indexOf(row);if(si<di)row.after(dragSrc);else row.before(dragSrc);}});
    row.addEventListener('drop',e=>e.preventDefault());
  });
}
async function saveOrder(){
  const order=[...document.querySelectorAll('.file-row')].map(r=>r.dataset.name);
  await fetch('/reorder',{method:'POST',body:JSON.stringify(order),headers:{'Content-Type':'application/json'}});
}

function updateBulk(){
  const n=document.querySelectorAll('.chk:checked').length;
  document.getElementById('selCount').textContent=n;
  document.getElementById('btnBulk').style.display=n>0?'inline-block':'none';
}
function toggleAll(){
  const chks=[...document.querySelectorAll('.chk')],all=chks.every(c=>c.checked);
  chks.forEach(c=>c.checked=!all);updateBulk();
}
async function delSelected(){
  const names=[...document.querySelectorAll('.chk:checked')].map(c=>c.value);
  if(!names.length||!confirm('Borrar '+names.length+' archivo(s)?'))return;
  await fetch('/delete-bulk',{method:'POST',body:JSON.stringify(names),headers:{'Content-Type':'application/json'}});
  load();
}
async function upload(files){
  for(const f of files){const fd=new FormData();fd.append('file',f);await fetch('/upload',{method:'POST',body:fd});}
  load();
}
async function del(name){
  if(!confirm('Borrar '+name+'?'))return;
  await fetch('/delete',{method:'POST',body:name,headers:{'Content-Type':'text/plain'}});
  load();
}

// ── Schedule modal ──
function openSlotModal(slot){
  document.getElementById('slotId').value = slot?.id ?? '';
  document.getElementById('slotName').value = slot?.name ?? '';
  document.getElementById('slotStart').value = slot ? minToTime(slot.startMinute) : '09:00';
  document.getElementById('slotEnd').value = slot ? minToTime(slot.endMinute) : '18:00';
  document.getElementById('slotInterval').value = slot?.intervalSeconds ?? 10;
  document.getElementById('modalTitle').textContent = slot ? 'Editar horario' : 'Nuevo horario';
  // Days
  const activeDays = new Set(slot?.days ?? [2,3,4,5,6]);
  document.querySelectorAll('.day-btn').forEach(b=>{
    b.classList.toggle('on', activeDays.has(Number(b.dataset.day)));
    b.onclick=()=>b.classList.toggle('on');
  });
  // Files
  editingSlotFiles = slot?.files ?? [];
  document.getElementById('fileCheckList').innerHTML = allFiles.length
    ? allFiles.map(f=>`<div class="file-check-row">
        <input type="checkbox" id="fc_${esc(f.name)}" value="${esc(f.name)}" ${editingSlotFiles.includes(f.name)?'checked':''}>
        <label for="fc_${esc(f.name)}">${esc(f.name)}</label>
      </div>`).join('')
    : '<p style="color:#5a6585;font-size:12px">Sin archivos cargados</p>';
  document.getElementById('overlay').classList.add('open');
}
function closeModal(){document.getElementById('overlay').classList.remove('open');}
document.getElementById('overlay').addEventListener('click',e=>{if(e.target===e.currentTarget)closeModal();});

async function saveSlot(){
  const id=document.getElementById('slotId').value;
  const name=document.getElementById('slotName').value.trim();
  if(!name){alert('El nombre es obligatorio');return;}
  const days=[...document.querySelectorAll('.day-btn.on')].map(b=>Number(b.dataset.day));
  if(!days.length){alert('Selecciona al menos un dia');return;}
  const files=[...document.querySelectorAll('[id^=fc_]:checked')].map(c=>c.value);
  const slot={
    id: id||crypto.randomUUID(),
    name,
    startMinute: timeToMin(document.getElementById('slotStart').value),
    endMinute: timeToMin(document.getElementById('slotEnd').value),
    days, files, enabled:true,
    intervalSeconds: Number(document.getElementById('slotInterval').value)||10
  };
  const method=id?'PUT':'POST';
  await fetch('/api/schedule',{method,body:JSON.stringify(slot),headers:{'Content-Type':'application/json'}});
  closeModal(); load();
}

async function delSlot(id){
  if(!confirm('Borrar este horario?'))return;
  await fetch('/api/schedule',{method:'DELETE',body:id,headers:{'Content-Type':'text/plain'}});
  load();
}

load();
</script></body></html>"""
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/zenscreen/display/WifiServer.kt
git commit -m "feat: add WifiServer with file management and schedule CRUD"
```

---

## Task 8: DisplayActivity

**Files:**
- Create: `app/src/main/java/com/zenscreen/display/DisplayActivity.kt`

Key differences from ZenScreen's KioskActivity:
- No PIN, no back button block, no launcher
- Checks schedule every 60 seconds via a coroutine loop
- If active slot changes → reload media files for that slot
- Default playlist when no slot is active
- No tap navigation (display = no touch expected)
- fade-in transitions on slide change

- [ ] **Step 1: Create `DisplayActivity.kt`**

```kotlin
package com.zenscreen.display

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
import com.zenscreen.display.databinding.ActivityDisplayBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class DisplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDisplayBinding
    private var slideshow = SlideshowManager<MediaFile>(emptyList())
    private var slideshowJob: Job? = null
    private var renderJob: Job? = null
    private var scheduleJob: Job? = null
    private var currentRenderer: PdfPageRenderer? = null
    private var exoPlayer: ExoPlayer? = null
    private val renderMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** ID of the last active slot (or null = default playlist). Used to detect changes. */
    private var lastActiveSlotId: String? = "INIT" // INIT forces first load

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        hideSystemUI()
        startServer()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        startScheduleLoop()
    }

    override fun onPause() {
        super.onPause()
        scheduleJob?.cancel()
        slideshowJob?.cancel()
        renderJob?.cancel()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        slideshowJob?.cancel()
        renderJob?.cancel()
        scheduleJob?.cancel()
        currentRenderer?.close()
        releasePlayer()
        scope.cancel()
        super.onDestroy()
    }

    // ── System UI ──────────────────────────────────────────────────────────────

    private fun hideSystemUI() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startServer() {
        startForegroundService(Intent(this, ServerService::class.java))
    }

    // ── Schedule loop ──────────────────────────────────────────────────────────

    /**
     * Polls the schedule every 60 seconds.
     * If the active slot changes (or on first run), reloads the playlist.
     */
    private fun startScheduleLoop() {
        scheduleJob?.cancel()
        scheduleJob = scope.launch {
            while (isActive) {
                checkAndReload()
                delay(60_000L)
            }
        }
    }

    private suspend fun checkAndReload() {
        val slots  = Prefs.getScheduleSlots(this@DisplayActivity)
        val active = ScheduleManager.activeSlot(
            slots, ScheduleManager.nowMinute(), ScheduleManager.todayDayOfWeek()
        )
        val activeId = active?.id  // null = default playlist

        if (activeId == lastActiveSlotId) return  // no change
        lastActiveSlotId = activeId

        val dir   = withContext(Dispatchers.IO) { FileManager.getMediaDir(this@DisplayActivity) }
        val order = Prefs.getPlaylistOrder(this@DisplayActivity)
        val interval: Long

        val files = if (active != null) {
            // Slot active: play only the slot's files
            interval = active.intervalSeconds
            withContext(Dispatchers.IO) {
                active.files.mapNotNull { name ->
                    val f = File(dir, name)
                    if (f.exists()) FileManager.mediaTypeOf(f)?.let { MediaFile(f, it) } else null
                }
            }
        } else {
            // Default playlist
            interval = Prefs.getIntervalSeconds(this@DisplayActivity)
            withContext(Dispatchers.IO) { FileManager.listMediaOrdered(dir, order) }
        }

        val currentName = if (!slideshow.isEmpty()) slideshow.current().file.name else null
        slideshow.reload(files)
        if (currentName != null) {
            val idx = files.indexOfFirst { it.file.name == currentName }
            if (idx >= 0) slideshow.seekTo(idx)
        }

        Prefs.setIntervalSeconds(this@DisplayActivity, interval)

        if (slideshow.isEmpty()) {
            showEmpty(); return
        }
        binding.emptyText.visibility = View.GONE
        renderCurrent()
    }

    // ── Slideshow ──────────────────────────────────────────────────────────────

    private fun renderCurrent() {
        if (slideshow.isEmpty()) return
        val mf = slideshow.current()
        renderJob?.cancel()
        renderJob = scope.launch {
            when (mf.type) {
                MediaType.PDF   -> renderPdf(mf.file)
                MediaType.IMAGE -> renderImage(mf.file)
                MediaType.VIDEO -> renderVideo(mf.file)
            }
        }
    }

    private fun advance() { slideshow.next(); renderCurrent() }

    private fun showEmpty() {
        binding.emptyText.visibility = View.VISIBLE
        binding.imageView.visibility = View.GONE
        binding.playerView.visibility = View.GONE
    }

    // ── PDF ────────────────────────────────────────────────────────────────────

    private suspend fun renderPdf(file: File) {
        stopVideo(); showImageView()
        withContext(Dispatchers.IO) {
            val w = binding.imageView.width.takeIf { it > 0 } ?: 1920
            val h = binding.imageView.height.takeIf { it > 0 } ?: 1080
            renderMutex.withLock {
                if (!isActive) return@withLock
                try {
                    currentRenderer?.close(); currentRenderer = null
                    val renderer = PdfPageRenderer(file)
                    currentRenderer = renderer
                    val bitmap = renderer.renderPage(0, w, h)
                    if (isActive) withContext(Dispatchers.Main) {
                        binding.imageView.setImageBitmap(bitmap); fadeInImage()
                    }
                } catch (_: Exception) { if (isActive) withContext(Dispatchers.Main) { advance() } }
            }
        }
        if (coroutineContext.isActive) startTimer()
    }

    // ── Image ──────────────────────────────────────────────────────────────────

    private suspend fun renderImage(file: File) {
        stopVideo(); showImageView()
        withContext(Dispatchers.IO) {
            renderMutex.withLock {
                if (!isActive) return@withLock
                try {
                    currentRenderer?.close(); currentRenderer = null
                    val w = binding.imageView.width.takeIf { it > 0 } ?: 1920
                    val h = binding.imageView.height.takeIf { it > 0 } ?: 1080
                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                        BitmapFactory.decodeFile(file.absolutePath, this)
                        inSampleSize = calcSampleSize(outWidth, outHeight, w, h)
                        inJustDecodeBounds = false
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (isActive && bitmap != null) withContext(Dispatchers.Main) {
                        binding.imageView.setImageBitmap(bitmap); fadeInImage()
                    } else if (isActive) withContext(Dispatchers.Main) { advance() }
                } catch (_: Exception) { if (isActive) withContext(Dispatchers.Main) { advance() } }
            }
        }
        if (coroutineContext.isActive) startTimer()
    }

    private fun calcSampleSize(w: Int, h: Int, rW: Int, rH: Int): Int {
        var s = 1
        if (h > rH || w > rW) { val hH=h/2; val hW=w/2
            while (hH/s>=rH && hW/s>=rW) s*=2 }
        return s
    }

    // ── Video ──────────────────────────────────────────────────────────────────

    private suspend fun renderVideo(file: File) {
        withContext(Dispatchers.IO) { renderMutex.withLock { currentRenderer?.close(); currentRenderer=null } }
        if (!coroutineContext.isActive) return
        slideshowJob?.cancel(); showVideoView(); releasePlayer()
        val player = ExoPlayer.Builder(this@DisplayActivity).build()
        exoPlayer = player; binding.playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> fadeInVideo()
                    Player.STATE_ENDED -> advance()
                }
            }
        })
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare(); player.playWhenReady = true
    }

    private fun releasePlayer() { exoPlayer?.release(); exoPlayer=null; binding.playerView.player=null }
    private fun stopVideo() { releasePlayer() }

    // ── Views ──────────────────────────────────────────────────────────────────

    private fun showImageView() {
        binding.imageView.setImageBitmap(null)
        binding.imageView.alpha = 0f
        binding.imageView.visibility = View.VISIBLE
        binding.playerView.visibility = View.GONE
    }

    private fun fadeInImage() {
        binding.imageView.animate().alpha(1f).setDuration(400).start()
    }

    private fun showVideoView() {
        binding.playerView.alpha = 0f
        binding.imageView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE
    }

    private fun fadeInVideo() {
        binding.playerView.animate().alpha(1f).setDuration(400).start()
    }

    // ── Timer ──────────────────────────────────────────────────────────────────

    private fun startTimer() {
        slideshowJob?.cancel()
        val interval = Prefs.getIntervalSeconds(this) * 1000L
        slideshowJob = scope.launch { delay(interval); if (isActive) advance() }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/zenscreen/display/DisplayActivity.kt
git commit -m "feat: add DisplayActivity with schedule polling and media rendering"
```

---

## Task 9: Full Build Verification

- [ ] **Step 1: Assemble debug APK**

```bash
cd /Users/wil/Documents/vibe-projects/zenscreen-display
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run all unit tests**

```bash
./gradlew test 2>&1 | tail -10
```

Expected: `15 tests completed, 0 failed` (10 ScheduleManager + 5 SlideshowManager)

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat: zenscreen-display v1.0.0 — scheduled media player for Android displays"
```

---

## Self-Review

**Spec coverage:**
- Scheduling (content X at time Y) — Task 3 (ScheduleManager) + Task 8 (DisplayActivity polls every 60s)
- Midnight-spanning slots — Task 3 tests cover it
- No kiosk lockdown — DisplayActivity has no back-block, no PIN, no launcher intent
- File management — Task 7 (WifiServer)
- Schedule CRUD via WiFi — Task 7 (dashboard + REST API)
- Auto-start on boot — Task 6 (BootReceiver)
- Fade transitions — Task 8 (DisplayActivity)
- Default playlist when no slot active — Task 8 (checkAndReload)
- Slot's own interval (not global) — Task 8 uses `active.intervalSeconds`

**Placeholder scan:** None found. All steps contain complete code.

**Type consistency check:**
- `ScheduleSlot.startMinute/endMinute` — consistent across ScheduleManager, Prefs, WifiServer, DisplayActivity
- `FileManager.getMediaDir()` — used in DisplayActivity and ServerService
- `ScheduleManager.activeSlot(slots, nowMinute, dayOfWeek)` — signature matches all call sites
- `Prefs.getScheduleSlots/setScheduleSlots/addOrUpdateSlot/deleteSlot` — all defined in Task 4, all used in Task 7
