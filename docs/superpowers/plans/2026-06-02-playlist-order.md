# Playlist Drag & Drop — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar un playlist con drag & drop en el panel admin para que el administrador pueda reordenar los archivos del slideshow manualmente.

**Architecture:** El orden del playlist se persiste en `Prefs` como lista de nombres de archivo separados por `\n`. `FileManager.listMediaOrdered()` devuelve los archivos respetando ese orden (archivos nuevos se agregan al final). `PlaylistAdapter` es un `RecyclerView.Adapter` con drag handles que dispara `ItemTouchHelper` al tocar el handle. `AdminActivity` conecta todo con un `ItemTouchHelper.SimpleCallback` que guarda el nuevo orden en Prefs tras cada movimiento.

**Tech Stack:** Kotlin, RecyclerView + ItemTouchHelper (AndroidX), SharedPreferences.

---

## File Structure

```
Modify: app/build.gradle.kts                                    ← add recyclerview dep
Modify: app/src/main/java/com/freekiosk/app/Prefs.kt            ← getPlaylistOrder / setPlaylistOrder
Modify: app/src/main/java/com/freekiosk/app/FileManager.kt      ← listMediaOrdered()
Modify: app/src/test/java/com/freekiosk/app/FileManagerTest.kt  ← tests listMediaOrdered
Create: app/src/main/res/layout/item_playlist.xml               ← row: handle + icon + name + delete
Create: app/src/main/java/com/freekiosk/app/PlaylistAdapter.kt  ← RecyclerView adapter
Create: app/src/main/res/drawable/ic_drag_handle.xml            ← drag handle icon
Modify: app/src/main/res/layout/activity_admin.xml              ← add RecyclerView section
Modify: app/src/main/java/com/freekiosk/app/AdminActivity.kt    ← wire RecyclerView + ItemTouchHelper
Modify: app/src/main/java/com/freekiosk/app/KioskActivity.kt    ← use listMediaOrdered
```

---

## Task 1: Prefs.getPlaylistOrder + FileManager.listMediaOrdered()

**Files:**
- Modify: `app/src/main/java/com/freekiosk/app/Prefs.kt`
- Modify: `app/src/main/java/com/freekiosk/app/FileManager.kt`
- Modify: `app/src/test/java/com/freekiosk/app/FileManagerTest.kt`

- [ ] **Step 1: Agregar tests para listMediaOrdered**

Al final de `FileManagerTest.kt`, agrega:

```kotlin
@Test
fun `listMediaOrdered respects stored order`() {
    val dir = tmp.newFolder("ordered")
    File(dir, "c.pdf").createNewFile()
    File(dir, "a.jpg").createNewFile()
    File(dir, "b.mp4").createNewFile()

    val result = FileManager.listMediaOrdered(dir, listOf("b.mp4", "c.pdf", "a.jpg"))

    assertEquals("b.mp4", result[0].file.name)
    assertEquals("c.pdf", result[1].file.name)
    assertEquals("a.jpg", result[2].file.name)
}

@Test
fun `listMediaOrdered appends new files not in order at end`() {
    val dir = tmp.newFolder("append")
    File(dir, "known.pdf").createNewFile()
    File(dir, "newfile.jpg").createNewFile()  // not in stored order

    val result = FileManager.listMediaOrdered(dir, listOf("known.pdf"))

    assertEquals("known.pdf", result[0].file.name)
    assertEquals("newfile.jpg", result[1].file.name)
}

@Test
fun `listMediaOrdered skips names that no longer exist on disk`() {
    val dir = tmp.newFolder("skip")
    File(dir, "exists.pdf").createNewFile()
    // "deleted.mp4" is in order but not on disk

    val result = FileManager.listMediaOrdered(dir, listOf("deleted.mp4", "exists.pdf"))

    assertEquals(1, result.size)
    assertEquals("exists.pdf", result[0].file.name)
}

@Test
fun `listMediaOrdered with empty order returns alphabetical`() {
    val dir = tmp.newFolder("empty_order")
    File(dir, "b.pdf").createNewFile()
    File(dir, "a.jpg").createNewFile()

    val result = FileManager.listMediaOrdered(dir, emptyList())

    assertEquals("a.jpg", result[0].file.name)
    assertEquals("b.pdf", result[1].file.name)
}
```

- [ ] **Step 2: Correr tests — deben fallar**

```bash
./gradlew :app:test 2>&1 | grep -E "listMediaOrdered" | head -5
```
Esperado: FAILED — `listMediaOrdered` no existe.

- [ ] **Step 3: Agregar getPlaylistOrder/setPlaylistOrder a Prefs.kt**

Agrega al final de `Prefs.kt`, antes del cierre `}`:

```kotlin
private const val KEY_PLAYLIST = "playlist_order"

fun getPlaylistOrder(context: Context): List<String> {
    val stored = prefs(context).getString(KEY_PLAYLIST, "") ?: ""
    return if (stored.isEmpty()) emptyList() else stored.split("\n")
}

fun setPlaylistOrder(context: Context, order: List<String>) {
    prefs(context).edit()
        .putString(KEY_PLAYLIST, order.joinToString("\n"))
        .apply()
}
```

- [ ] **Step 4: Agregar listMediaOrdered a FileManager.kt**

Agrega después de `listMedia()`:

```kotlin
/**
 * Lista archivos de media respetando el [order] guardado.
 * - Archivos en [order] que existen en disco → en el orden indicado
 * - Archivos nuevos no en [order] → al final, ordenados por nombre
 * - Nombres en [order] que ya no existen en disco → ignorados
 */
fun listMediaOrdered(dir: File, order: List<String>): List<MediaFile> {
    val allFiles = listMedia(dir)
    val filesByName = allFiles.associateBy { it.file.name }

    val orderedFiles = order.mapNotNull { name -> filesByName[name] }
    val newFiles = allFiles.filter { mf -> mf.file.name !in order }

    return orderedFiles + newFiles
}
```

- [ ] **Step 5: Correr tests — deben pasar**

```bash
./gradlew :app:test 2>&1 | tail -8
```
Esperado: BUILD SUCCESSFUL, todos los tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/Prefs.kt \
        app/src/main/java/com/freekiosk/app/FileManager.kt \
        app/src/test/java/com/freekiosk/app/FileManagerTest.kt
git commit -m "feat: playlist order in Prefs + FileManager.listMediaOrdered()"
```

---

## Task 2: Dependencia RecyclerView + layouts del playlist

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/res/drawable/ic_drag_handle.xml`
- Create: `app/src/main/res/layout/item_playlist.xml`

- [ ] **Step 1: Agregar RecyclerView a build.gradle.kts**

En el bloque `dependencies { }`:

```kotlin
implementation("androidx.recyclerview:recyclerview:1.3.2")
```

- [ ] **Step 2: Crear ic_drag_handle.xml**

```xml
<!-- app/src/main/res/drawable/ic_drag_handle.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#64748b"
        android:pathData="M20,9H4v2h16V9zM4,15h16v-2H4v2z"/>
</vector>
```

- [ ] **Step 3: Crear item_playlist.xml**

```xml
<!-- app/src/main/res/layout/item_playlist.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingVertical="10dp"
    android:background="#1a1d27">

    <!-- Drag handle -->
    <ImageView
        android:id="@+id/dragHandle"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:padding="8dp"
        android:src="@drawable/ic_drag_handle"
        android:contentDescription="Arrastrar para reordenar" />

    <!-- Ícono de tipo -->
    <TextView
        android:id="@+id/typeIcon"
        android:layout_width="32dp"
        android:layout_height="wrap_content"
        android:textSize="18sp"
        android:gravity="center" />

    <!-- Nombre del archivo -->
    <TextView
        android:id="@+id/fileName"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="#e2e8f0"
        android:textSize="13sp"
        android:ellipsize="middle"
        android:singleLine="true"
        android:paddingHorizontal="8dp" />

    <!-- Botón eliminar -->
    <ImageButton
        android:id="@+id/deleteBtn"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:padding="8dp"
        android:src="@android:drawable/ic_menu_delete"
        android:background="@android:color/transparent"
        android:contentDescription="Eliminar" />

</LinearLayout>
```

- [ ] **Step 4: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/res/drawable/ic_drag_handle.xml \
        app/src/main/res/layout/item_playlist.xml
git commit -m "feat: RecyclerView dep + playlist item layout and drag handle icon"
```

---

## Task 3: PlaylistAdapter

**Files:**
- Create: `app/src/main/java/com/freekiosk/app/PlaylistAdapter.kt`

- [ ] **Step 1: Crear PlaylistAdapter.kt**

```kotlin
// app/src/main/java/com/freekiosk/app/PlaylistAdapter.kt
package com.freekiosk.app

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.freekiosk.app.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    val items: MutableList<MediaFile>,
    private val onDelete: (MediaFile) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = items[position]
        with(holder.binding) {
            typeIcon.text = when (media.type) {
                MediaType.PDF   -> "📄"
                MediaType.IMAGE -> "🖼️"
                MediaType.VIDEO -> "🎬"
            }
            fileName.text = media.file.name

            deleteBtn.setOnClickListener {
                onDelete(media)
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }
    }

    override fun getItemCount() = items.size

    /** Mueve un item de [from] a [to] — llamado por ItemTouchHelper durante el drag. */
    fun moveItem(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    /** Devuelve los nombres de archivo en el orden actual. */
    fun currentOrder(): List<String> = items.map { it.file.name }
}
```

- [ ] **Step 2: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/PlaylistAdapter.kt
git commit -m "feat: PlaylistAdapter with drag handle and delete support"
```

---

## Task 4: activity_admin.xml — agregar sección playlist

**Files:**
- Modify: `app/src/main/res/layout/activity_admin.xml`

- [ ] **Step 1: Agregar sección playlist antes de "Configuración avanzada"**

Localiza el comentario `<!-- Configuración avanzada -->` en `activity_admin.xml` e inserta este bloque **antes** de él:

```xml
<!-- Playlist — orden del slideshow -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#1a1d27"
    android:padding="16dp"
    android:layout_marginBottom="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="12dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="PLAYLIST (arrastra para reordenar)"
            android:textColor="#64748b"
            android:textSize="11sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSavePlaylist"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Guardar orden"
            android:textSize="11sp"
            android:paddingHorizontal="12dp"
            android:backgroundTint="#4f8ef7" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/playlistRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:nestedScrollingEnabled="false" />

    <TextView
        android:id="@+id/playlistEmptyText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Sin archivos todavía"
        android:textColor="#64748b"
        android:textSize="13sp"
        android:visibility="gone" />

</LinearLayout>
```

- [ ] **Step 2: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_admin.xml
git commit -m "feat: add playlist RecyclerView section to admin layout"
```

---

## Task 5: AdminActivity — conectar RecyclerView + ItemTouchHelper

**Files:**
- Modify: `app/src/main/java/com/freekiosk/app/AdminActivity.kt`

- [ ] **Step 1: Agregar imports al inicio de AdminActivity.kt**

Agrega estos imports (junto a los existentes):

```kotlin
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
```

- [ ] **Step 2: Agregar propiedad del adapter a AdminActivity**

Después de `private lateinit var binding: ActivityAdminBinding`, agrega:

```kotlin
private lateinit var playlistAdapter: PlaylistAdapter
```

- [ ] **Step 3: Llamar setupPlaylist() en onCreate**

En `onCreate`, después de `setupAdvancedSettings()`:

```kotlin
setupPlaylist()
```

- [ ] **Step 4: Llamar refreshPlaylist() en onResume**

En `onResume`, agrega al final:

```kotlin
refreshPlaylist()
```

- [ ] **Step 5: Agregar los métodos setupPlaylist y refreshPlaylist**

Agrega antes del método `setupButtons()`:

```kotlin
// ── Playlist ────────────────────────────────────────────────────────────────

private fun setupPlaylist() {
    val dir = FileManager.getPdfsDir(this)
    val order = Prefs.getPlaylistOrder(this)
    val items = FileManager.listMediaOrdered(dir, order).toMutableList()

    playlistAdapter = PlaylistAdapter(
        items = items,
        onDelete = { media ->
            FileManager.deleteFile(media.file)
            // Actualizar orden guardado
            val newOrder = playlistAdapter.currentOrder().filter { it != media.file.name }
            Prefs.setPlaylistOrder(this, newOrder)
            refreshPlaylist()
            refreshFileCount()
        },
        onStartDrag = { holder -> touchHelper.startDrag(holder) }
    )

    binding.playlistRecyclerView.apply {
        layoutManager = LinearLayoutManager(this@AdminActivity)
        adapter = playlistAdapter
    }
    touchHelper.attachToRecyclerView(binding.playlistRecyclerView)

    binding.btnSavePlaylist.setOnClickListener {
        Prefs.setPlaylistOrder(this, playlistAdapter.currentOrder())
        toast("Orden guardado")
    }

    updatePlaylistEmptyState(items)
}

private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        playlistAdapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Auto-guardar al soltar
        Prefs.setPlaylistOrder(this@AdminActivity, playlistAdapter.currentOrder())
    }
})

private fun refreshPlaylist() {
    val dir = FileManager.getPdfsDir(this)
    val order = Prefs.getPlaylistOrder(this)
    val items = FileManager.listMediaOrdered(dir, order).toMutableList()
    playlistAdapter.items.clear()
    playlistAdapter.items.addAll(items)
    playlistAdapter.notifyDataSetChanged()
    updatePlaylistEmptyState(items)
}

private fun updatePlaylistEmptyState(items: List<MediaFile>) {
    binding.playlistEmptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    binding.playlistRecyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
}
```

- [ ] **Step 6: Verificar build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Esperado: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/AdminActivity.kt
git commit -m "feat: AdminActivity playlist with drag-to-reorder and auto-save"
```

---

## Task 6: KioskActivity — usar listMediaOrdered

**Files:**
- Modify: `app/src/main/java/com/freekiosk/app/KioskActivity.kt`

- [ ] **Step 1: Actualizar reloadAndStart() para usar el orden guardado**

Localiza el método `reloadAndStart()` en `KioskActivity.kt`.

Encuentra esta línea:
```kotlin
val files = FileManager.listMedia(dir)
```

Reemplaza con:
```kotlin
val order = Prefs.getPlaylistOrder(this)
val files = FileManager.listMediaOrdered(dir, order)
```

- [ ] **Step 2: Verificar build y tests**

```bash
./gradlew :app:assembleDebug :app:test 2>&1 | tail -8
```
Esperado: BUILD SUCCESSFUL, todos los tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/freekiosk/app/KioskActivity.kt
git commit -m "feat: KioskActivity uses playlist order from Prefs"
```

---

## Task 7: Build final

- [ ] **Step 1: Build limpio**

```bash
find app/build -name "* 2.*" -delete 2>/dev/null
./gradlew clean assembleDebug 2>&1 | tail -5
```
Esperado: BUILD SUCCESSFUL.

- [ ] **Step 2: Instalar**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Verificar flujo completo**

1. Sube 3+ archivos de tipos variados (PDF, imagen, video)
2. Abre panel admin → sección **PLAYLIST** muestra los archivos
3. Arrastra un archivo a otra posición → el orden cambia
4. Al soltar, el orden se guarda automáticamente
5. Toca **Guardar orden** como alternativa
6. Toca **Volver al kiosk** → el slideshow respeta el nuevo orden
7. Elimina un archivo desde el playlist → desaparece del listado y del disco

- [ ] **Step 4: Commit final si hay cambios**

```bash
git status
git add -A && git commit -m "chore: playlist drag & drop end-to-end verified" 2>/dev/null || echo "no changes"
```
