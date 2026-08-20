package com.zenscreen.app

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zenscreen.app.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var mediaAdapter: MediaFileAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { importMedia(it) }; refreshFileList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setupVersion()
        setupServerUrl()
        setupFilesList()
        refreshFileList()
        setupSlider()
        setupOrientationButtons()
        setupAdvancedSettings()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshFileList()
        setupServerUrl()
        updateLauncherButton()
    }

    private fun setupVersion() {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        binding.versionText.text = "ZenScreen v${pInfo.versionName} (build ${pInfo.versionCode})"
    }

    private fun setupServerUrl() {
        val ip = getLocalIp()
        val port = Prefs.getServerPort(this)
        binding.serverUrlText.text = "http://$ip:$port"
    }

    private fun getLocalIp(): String {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private fun setupFilesList() {
        mediaAdapter = MediaFileAdapter(
            onDelete = { mf -> confirmDelete(mf) },
            onSelectionChanged = { count -> updateBulkDeleteButton(count) },
            onStartDrag = { vh -> itemTouchHelper.startDrag(vh) }
        )

        binding.filesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdminActivity)
            adapter = mediaAdapter
            isNestedScrollingEnabled = false
        }

        val dragCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                mediaAdapter.moveItem(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                saveOrder()
            }
        }
        itemTouchHelper = ItemTouchHelper(dragCallback)
        itemTouchHelper.attachToRecyclerView(binding.filesRecyclerView)

        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
    }

    private fun refreshFileList() {
        val dir = FileManager.getPdfsDir(this)
        val order = Prefs.getPlaylistOrder(this)
        val all = FileManager.listMediaOrdered(dir, order)

        val pdfs   = all.count { it.type == MediaType.PDF }
        val images = all.count { it.type == MediaType.IMAGE }
        val videos = all.count { it.type == MediaType.VIDEO }
        binding.fileCountText.text = buildString {
            append("${all.size} archivo(s) en la tablet")
            if (all.isNotEmpty()) {
                append("\n")
                val parts = mutableListOf<String>()
                if (pdfs   > 0) parts.add("$pdfs PDF")
                if (images > 0) parts.add("$images imagen${if (images > 1) "es" else ""}")
                if (videos > 0) parts.add("$videos video${if (videos > 1) "s" else ""}")
                append(parts.joinToString(" · "))
            }
        }
        mediaAdapter.submitList(all)
    }

    private fun saveOrder() {
        val order = mediaAdapter.getItems().map { it.file.name }
        Prefs.setPlaylistOrder(this, order)
    }

    private fun updateBulkDeleteButton(selectedCount: Int) {
        binding.btnDeleteSelected.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
        binding.btnDeleteSelected.text = "Borrar seleccionados ($selectedCount)"
    }

    private fun confirmDelete(mf: MediaFile) {
        AlertDialog.Builder(this)
            .setTitle("Borrar archivo")
            .setMessage("¿Borrar ${mf.file.name}?")
            .setPositiveButton("Borrar") { _, _ ->
                mf.file.delete()
                val newOrder = Prefs.getPlaylistOrder(this).filter { it != mf.file.name }
                Prefs.setPlaylistOrder(this, newOrder)
                refreshFileList()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteSelected() {
        val names = mediaAdapter.getSelectedNames()
        if (names.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Borrar archivos")
            .setMessage("¿Borrar ${names.size} archivo(s) seleccionado(s)?")
            .setPositiveButton("Borrar") { _, _ ->
                val dir = FileManager.getPdfsDir(this)
                names.forEach { java.io.File(dir, it).delete() }
                val newOrder = Prefs.getPlaylistOrder(this).filter { it !in names }
                Prefs.setPlaylistOrder(this, newOrder)
                mediaAdapter.clearSelection()
                refreshFileList()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun importMedia(uri: Uri) {
        val dir = FileManager.getPdfsDir(this)
        val name = getFileName(uri) ?: "import_${System.currentTimeMillis()}"
        // Solo importar si el tipo es soportado
        if (FileManager.mediaTypeOf(java.io.File(name)) == null) {
            toast("Formato no soportado: $name")
            return
        }
        contentResolver.openInputStream(uri)?.use { input ->
            FileManager.saveMedia(dir, name, input.readBytes())
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
        val initial = when (Prefs.getOrientation(this)) {
            "landscape" -> binding.btnOrientLandscape.id
            "sensor"    -> binding.btnOrientSensor.id
            else        -> binding.btnOrientPortrait.id
        }
        binding.orientationToggleGroup.check(initial)

        binding.orientationToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = when (checkedId) {
                binding.btnOrientLandscape.id -> "landscape"
                binding.btnOrientSensor.id    -> "sensor"
                else                          -> "portrait"
            }
            Prefs.setOrientation(this, value)
        }
    }

    // ── Configuración avanzada ──────────────────────────────────────────────

    private fun setupAdvancedSettings() {
        binding.btnChangePin.setOnClickListener { showChangePinDialog() }
        binding.btnChangePort.setOnClickListener { showChangePortDialog() }
        binding.btnChangeServerAuth.setOnClickListener { showChangeServerAuthDialog() }
    }

    private fun showChangePinDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val currentInput = EditText(this).apply {
            hint = "PIN actual"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val newInput = EditText(this).apply {
            hint = "PIN nuevo (mínimo 4 dígitos)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val confirmInput = EditText(this).apply {
            hint = "Confirmar PIN nuevo"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        layout.addView(currentInput)
        layout.addView(newInput)
        layout.addView(confirmInput)

        AlertDialog.Builder(this)
            .setTitle("Cambiar PIN")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val current = currentInput.text.toString()
                val new = newInput.text.toString()
                val confirm = confirmInput.text.toString()
                when {
                    current != Prefs.getPin(this) ->
                        toast("PIN actual incorrecto")
                    new.length < 4 ->
                        toast("El PIN debe tener al menos 4 dígitos")
                    new != confirm ->
                        toast("Los PINs no coinciden")
                    else -> {
                        Prefs.setPin(this, new)
                        toast("PIN actualizado correctamente")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChangePortDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val portInput = EditText(this).apply {
            hint = "Puerto (1024–65535)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(Prefs.getServerPort(this@AdminActivity).toString())
        }
        val note = TextView(this).apply {
            text = "El servidor se reiniciará al guardar."
            textSize = 12f
            setTextColor(0xFF64748b.toInt())
            setPadding(0, 8, 0, 0)
        }
        layout.addView(portInput)
        layout.addView(note)

        AlertDialog.Builder(this)
            .setTitle("Cambiar puerto del servidor")
            .setView(layout)
            .setPositiveButton("Guardar y reiniciar") { _, _ ->
                val portStr = portInput.text.toString()
                val port = portStr.toIntOrNull()
                when {
                    port == null || port < 1024 || port > 65535 ->
                        toast("Puerto inválido (usa un valor entre 1024 y 65535)")
                    else -> {
                        Prefs.setServerPort(this, port)
                        restartServer()
                        setupServerUrl()
                        toast("Servidor reiniciado en puerto $port")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChangeServerAuthDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val userInput = EditText(this).apply {
            hint = "Usuario"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(Prefs.getServerUser(this@AdminActivity))
        }
        val passInput = EditText(this).apply {
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val note = TextView(this).apply {
            text = "Usuario actual: ${Prefs.getServerUser(this@AdminActivity)}\nDeja la contraseña vacía para mantener la actual."
            textSize = 12f
            setTextColor(0xFF64748b.toInt())
            setPadding(0, 8, 0, 0)
        }
        layout.addView(userInput)
        layout.addView(passInput)
        layout.addView(note)

        AlertDialog.Builder(this)
            .setTitle("Credenciales del servidor WiFi")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString()
                when {
                    user.isEmpty() ->
                        toast("El usuario no puede estar vacío")
                    else -> {
                        Prefs.setServerUser(this, user)
                        if (pass.isNotEmpty()) Prefs.setServerPass(this, pass)
                        toast("Credenciales actualizadas")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun restartServer() {
        val intent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_RESTART
        }
        stopService(Intent(this, ServerService::class.java))
        startForegroundService(intent)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun setupButtons() {
        binding.importUsbBtn.setOnClickListener {
            // "*/*" muestra todos los archivos; el usuario puede seleccionar PDF, imagen o video
            pickMedia.launch("*/*")
        }
        binding.setLauncherBtn.setOnClickListener {
            requestDefaultLauncher()
        }
        binding.backToKioskBtn.setOnClickListener {
            finish()
        }
    }

    private fun requestDefaultLauncher() {
        // Abrir ajustes de "App de inicio" — funciona en todos los Android sin permisos especiales
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            // Fallback: abrir ajustes generales de apps predeterminadas
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e2: Exception) {
                // Último recurso: ajustes generales
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun updateLauncherButton() {
        if (isDefaultLauncher()) {
            binding.setLauncherBtn.text = "ZenScreen es el launcher activo"
            binding.setLauncherBtn.setIconResource(R.drawable.ic_check)
            binding.setLauncherBtn.backgroundTintList = ColorStateList.valueOf(0xFF166534.toInt())
        } else {
            binding.setLauncherBtn.text = "Establecer como launcher"
            binding.setLauncherBtn.setIconResource(R.drawable.ic_home)
            binding.setLauncherBtn.backgroundTintList = ColorStateList.valueOf(0xFF22263a.toInt())
        }
    }

    companion object
}
