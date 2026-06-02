package com.freekiosk.app

import android.content.Context
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import java.io.File

class WifiServer(
    port: Int,
    private val pdfsDir: File,
    private val context: Context
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        // Verificar autenticación en todas las rutas
        val authResponse = checkAuth(session)
        if (authResponse != null) return authResponse

        return when {
            session.method == Method.GET  && session.uri == "/"             -> serveDashboard()
            session.method == Method.POST && session.uri == "/upload"       -> handleUpload(session)
            session.method == Method.POST && session.uri == "/delete"       -> handleDelete(session)
            session.method == Method.POST && session.uri == "/delete-bulk"  -> handleDeleteBulk(session)
            session.method == Method.POST && session.uri == "/reorder"      -> handleReorder(session)
            session.method == Method.GET  && session.uri == "/api/files"    -> serveFileList()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun checkAuth(session: IHTTPSession): Response? {
        val user = Prefs.getServerUser(context)
        val pass = Prefs.getServerPass(context)
        val expected = "Basic " + Base64.encodeToString(
            "$user:$pass".toByteArray(), Base64.NO_WRAP
        )
        val provided = session.headers["authorization"] ?: ""
        if (provided != expected) {
            val res = newFixedLengthResponse(
                Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Acceso no autorizado"
            )
            res.addHeader("WWW-Authenticate", "Basic realm=\"ZenScreen\"")
            return res
        }
        return null
    }

    private fun serveDashboard(): Response {
        val html = buildString {
            append("""<!DOCTYPE html><html lang="es"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ZenScreen Admin</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:system-ui,sans-serif;background:#0f1117;color:#e2e8f0;padding:24px}
h1{font-size:20px;margin-bottom:24px;color:#4f8ef7}
.card{background:#1a1d27;border:1px solid #2d3148;border-radius:12px;padding:20px;margin-bottom:20px}
h2{font-size:13px;color:#64748b;text-transform:uppercase;letter-spacing:.5px;margin-bottom:14px}
.drop{border:2px dashed #2d3148;border-radius:10px;padding:32px;text-align:center;cursor:pointer;position:relative}
.drop input{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%}
.drop:hover{border-color:#4f8ef7}
.file-row{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #2d3148;cursor:default;transition:background .15s;border-radius:6px}
.file-row:last-child{border:none}
.file-row.drag-over{background:#1e2540;border-radius:6px}
.file-row.dragging{opacity:.4}
.drag-handle{cursor:grab;color:#3d4460;font-size:18px;user-select:none;padding:0 4px;flex-shrink:0}
.drag-handle:active{cursor:grabbing}
.chk{width:16px;height:16px;accent-color:#4f8ef7;flex-shrink:0;cursor:pointer}
.file-info{flex:1;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.btn-del{background:rgba(239,68,68,.15);color:#ef4444;border:1px solid rgba(239,68,68,.2);border-radius:8px;padding:6px 12px;cursor:pointer;font-size:12px;flex-shrink:0}
.btn-del:hover{opacity:.8}
.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:12px;flex-wrap:wrap}
.btn-bulk{background:rgba(239,68,68,.2);color:#ef4444;border:1px solid rgba(239,68,68,.3);border-radius:8px;padding:7px 14px;cursor:pointer;font-size:13px;display:none}
.btn-bulk:hover{opacity:.8}
.btn-sel-all{background:#1e2540;color:#94a3b8;border:1px solid #2d3148;border-radius:8px;padding:7px 14px;cursor:pointer;font-size:13px}
.btn-sel-all:hover{background:#252b42}
.status{display:inline-block;width:8px;height:8px;border-radius:50%;background:#22c55e;margin-right:8px;animation:pulse 2s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
.order-hint{font-size:11px;color:#3d4460;margin-top:6px}
</style></head><body>
<h1>📺 ZenScreen · Gestión WiFi</h1>
<div class="card">
  <h2><span class="status"></span>Servidor activo</h2>
  <p style="color:#64748b;font-size:13px">Archivos en la tablet</p>
</div>
<div class="card">
  <h2>Subir archivos</h2>
  <div class="drop">
    <input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.mp4,.mkv,.mov,.avi,.webm" multiple onchange="upload(this.files)">
    <p style="color:#64748b">Arrastra archivos aquí o toca para seleccionar<br><small style="font-size:10px">PDF · JPG · PNG · WebP · MP4 · MKV · MOV</small></p>
  </div>
</div>
<div class="card">
  <h2>Archivos (<span id="count">...</span>)</h2>
  <div class="toolbar">
    <button class="btn-sel-all" onclick="toggleAll()">Seleccionar todo</button>
    <button class="btn-bulk" id="btnBulk" onclick="delSelected()">Borrar seleccionados (<span id="selCount">0</span>)</button>
  </div>
  <div id="list">Cargando...</div>
  <p class="order-hint">⠿ Arrastra las filas para cambiar el orden del slideshow</p>
</div>
<script>
let dragSrc=null;

function buildList(files){
  if(files.length===0) return '<p style="color:#64748b;font-size:13px">Sin archivos todavía</p>';
  return files.map(f=>`<div class="file-row" draggable="true" data-name="${'$'}{esc(f.name)}">
    <span class="drag-handle" title="Arrastrar para reordenar">⠿</span>
    <input type="checkbox" class="chk" value="${'$'}{esc(f.name)}" onchange="updateBulkBtn()">
    <span class="file-info">${'$'}{f.icon} ${'$'}{esc(f.name)} <span style="color:#64748b;font-size:11px">(${'$'}{(f.size/1024).toFixed(1)} KB)</span></span>
    <button class="btn-del" onclick="del('${'$'}{esc(f.name)}')">Borrar</button>
  </div>`).join('');
}

function esc(s){return s.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;');}

async function load(){
  const r=await fetch('/api/files');
  const d=await r.json();
  document.getElementById('count').textContent=d.files.length;
  document.getElementById('list').innerHTML=buildList(d.files);
  setupDrag();
  updateBulkBtn();
}

function setupDrag(){
  document.querySelectorAll('.file-row').forEach(row=>{
    row.addEventListener('dragstart',e=>{
      dragSrc=row;
      row.classList.add('dragging');
      e.dataTransfer.effectAllowed='move';
    });
    row.addEventListener('dragend',()=>{
      row.classList.remove('dragging');
      document.querySelectorAll('.file-row').forEach(r=>r.classList.remove('drag-over'));
      saveOrder();
    });
    row.addEventListener('dragover',e=>{
      e.preventDefault();
      if(dragSrc&&dragSrc!==row){
        row.classList.add('drag-over');
        const list=document.getElementById('list');
        const rows=[...list.querySelectorAll('.file-row')];
        const srcIdx=rows.indexOf(dragSrc);
        const dstIdx=rows.indexOf(row);
        if(srcIdx<dstIdx) row.after(dragSrc); else row.before(dragSrc);
      }
    });
    row.addEventListener('dragleave',()=>row.classList.remove('drag-over'));
    row.addEventListener('drop',e=>e.preventDefault());
  });
}

async function saveOrder(){
  const names=[...document.querySelectorAll('.file-row')].map(r=>r.dataset.name);
  await fetch('/reorder',{method:'POST',body:JSON.stringify(names),headers:{'Content-Type':'application/json'}});
}

function updateBulkBtn(){
  const n=document.querySelectorAll('.chk:checked').length;
  document.getElementById('selCount').textContent=n;
  document.getElementById('btnBulk').style.display=n>0?'inline-block':'none';
}

function toggleAll(){
  const chks=[...document.querySelectorAll('.chk')];
  const allChecked=chks.every(c=>c.checked);
  chks.forEach(c=>c.checked=!allChecked);
  updateBulkBtn();
}

async function delSelected(){
  const names=[...document.querySelectorAll('.chk:checked')].map(c=>c.value);
  if(!names.length)return;
  if(!confirm('¿Borrar '+names.length+' archivo(s)?'))return;
  await fetch('/delete-bulk',{method:'POST',body:JSON.stringify(names),headers:{'Content-Type':'application/json'}});
  load();
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

    private fun handleDeleteBulk(session: IHTTPSession): Response {
        return try {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            val json = body["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No body"
            )
            val names = parseJsonArray(json)
            names.forEach { name ->
                val safe = java.io.File(name).name
                java.io.File(pdfsDir, safe).delete()
            }
            // Limpiar del orden guardado los archivos eliminados
            val currentOrder = Prefs.getPlaylistOrder(context).filter { it !in names }
            Prefs.setPlaylistOrder(context, currentOrder)
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    private fun handleReorder(session: IHTTPSession): Response {
        return try {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            val json = body["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No body"
            )
            val order = parseJsonArray(json)
            Prefs.setPlaylistOrder(context, order)
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    /** Parsea un array JSON simple de strings: ["a","b","c"] */
    private fun parseJsonArray(json: String): List<String> {
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isBlank()) return emptyList()
        return trimmed.split(",").map { it.trim().removeSurrounding("\"") }
    }

    private fun serveFileList(): Response {
        val order = Prefs.getPlaylistOrder(context)
        val files = FileManager.listMediaOrdered(pdfsDir, order)
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
}
