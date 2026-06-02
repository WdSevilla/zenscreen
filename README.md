# ZenScreen

**ZenScreen** es una app Android de kiosk para exhibir PDFs en pantalla completa con modo de administración oculto, servidor WiFi integrado y arranque automático al encender el dispositivo.

Pensada para tabletas fijas en recepciones, salas de espera, exhibiciones o cualquier lugar donde se necesite mostrar contenido PDF de forma desatendida.

---

## Características

- 📄 **Slideshow de PDFs** — muestra todos los PDFs en bucle infinito
- ⏱️ **Intervalo configurable** — de 3 a 60 segundos por página
- 👆 **Toque para avanzar** — el usuario puede pasar páginas manualmente
- 🔒 **Modo kiosk** — bloquea el botón Atrás, oculta barras del sistema
- 🏠 **Launcher integrado** — al presionar Home vuelve al kiosk
- 🔄 **Arranque automático** — inicia solo al encender la tablet
- 📡 **Servidor WiFi** — sube/elimina PDFs desde cualquier PC en la misma red
- 🛠️ **Panel de administración** — accesible con toque largo + PIN
- 🔃 **Orientación configurable** — vertical, horizontal o automática

---

## Requisitos

| Requisito | Versión mínima |
|-----------|---------------|
| Android | 8.0 (API 26) |
| Android Studio | Hedgehog 2023.1+ |
| JDK | 17 |
| Gradle | 8.4 |

---

## Instalación rápida

### Opción A — Instalar APK directo

1. Descarga el APK desde [Releases](../../releases)
2. En la tablet: **Ajustes → Seguridad → Instalar apps desconocidas** → permitir
3. Abre el APK e instala
4. Abre ZenScreen — te pedirá permisos y te llevará a Ajustes para establecerlo como launcher

### Opción B — Compilar desde código

```bash
# Clonar el repositorio
git clone https://github.com/tu-usuario/zenscreen.git
cd zenscreen

# Compilar APK debug
./gradlew assembleDebug

# Instalar en tablet conectada por USB (con Depuración USB activada)
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Configuración inicial

Al abrir la app por primera vez sigue estos pasos:

### 1. Permisos
La app solicita automáticamente los permisos necesarios:
- Almacenamiento (para leer PDFs)
- Notificaciones (para el servidor WiFi en segundo plano)

### 2. Establecer como launcher
La app abre **Ajustes → App de inicio** del sistema. Selecciona **ZenScreen** para que el botón Home siempre regrese al kiosk.

> ⚠️ Este paso es opcional pero recomendado para uso en kiosk real. Sin él, el botón Home puede salir de la app.

### 3. Optimización de batería
Para que el servidor WiFi no sea detenido por Android:

**Ajustes → Aplicaciones → ZenScreen → Batería → Sin restricciones**

---

## Subir PDFs

### Vía WiFi (recomendado)
1. Conecta tu PC y la tablet a la misma red WiFi
2. Abre el panel de administración en la tablet (ver abajo)
3. Anota la URL que aparece en **Servidor WiFi** (ej: `http://192.168.1.100:8080`)
4. Abre esa URL en el browser de tu PC
5. Arrastra o selecciona los PDFs a subir

![Dashboard WiFi](docs/wifi-dashboard.png)

### Vía USB / almacenamiento local
1. Conecta la tablet por USB o usa un explorador de archivos
2. Navega a: `Android/data/com.freekiosk.app/files/pdfs/`
3. Copia tus PDFs ahí
4. El kiosk los detecta automáticamente en el próximo ciclo

---

## Panel de administración

El panel está oculto para evitar que usuarios accidentales lo abran.

### Cómo acceder
1. **Mantén presionada** la esquina superior derecha de la pantalla durante ~3 segundos
2. Ingresa el **PIN** (por defecto: `1234`)
3. Accede al panel

> 💡 El PIN se puede cambiar editando `Prefs.DEFAULT_PIN` en el código fuente (próximamente editable desde la interfaz).

### Opciones disponibles

| Sección | Descripción |
|---------|-------------|
| **Servidor WiFi** | Muestra la URL para subir archivos desde PC |
| **Archivos en tablet** | Cantidad de PDFs cargados + botón importar desde almacenamiento |
| **Intervalo slideshow** | Slider de 3 a 60 segundos |
| **Orientación de pantalla** | Vertical / Horizontal / Auto |
| **Establecer como launcher** | Abre ajustes del sistema para configurar como app de inicio |
| **Volver al kiosk** | Cierra el panel y regresa a la pantalla principal |

---

## Arquitectura

```
zenscreen/
├── app/src/main/java/com/freekiosk/app/
│   ├── MainActivity.kt          # Entry point: permisos → launcher → kiosk
│   ├── KioskActivity.kt         # Pantalla principal: slideshow fullscreen
│   ├── AdminActivity.kt         # Panel de configuración con PIN
│   ├── PinDialogFragment.kt     # Diálogo de PIN
│   ├── ServerService.kt         # Foreground service: mantiene servidor activo
│   ├── WifiServer.kt            # NanoHTTPD: dashboard HTML + API upload/delete
│   ├── BootReceiver.kt          # BroadcastReceiver: auto-start al encender
│   ├── PdfPageRenderer.kt       # Wrapper de Android PdfRenderer
│   ├── SlideshowManager.kt      # Navegación circular entre archivos
│   ├── FileManager.kt           # Operaciones de archivos PDF
│   └── Prefs.kt                 # SharedPreferences: PIN, intervalo, orientación
└── app/src/main/res/
    ├── layout/                  # Layouts XML
    └── values/                  # Strings, colores, temas
```

### Flujo de la app

```
Encendido
    └── BootReceiver
            ├── Inicia ServerService (puerto 8080)
            └── Abre KioskActivity
                    ├── Carga PDFs desde /pdfs/
                    ├── Slideshow en bucle con timer coroutine
                    ├── Toque → avanza al siguiente PDF
                    └── Toque largo (esquina) → PIN → AdminActivity
```

### Servidor WiFi

El servidor HTTP embebido (NanoHTTPD) expone:

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/` | GET | Dashboard HTML para gestionar archivos |
| `/api/files` | GET | Lista de PDFs en JSON |
| `/upload` | POST | Subir PDF (multipart/form-data) |
| `/delete` | POST | Eliminar PDF por nombre |

---

## Configuración avanzada

### Cambiar PIN por defecto
En `Prefs.kt`:
```kotlin
const val DEFAULT_PIN = "1234"  // Cambia aquí
```

### Cambiar puerto del servidor WiFi
En `ServerService.kt`:
```kotlin
const val SERVER_PORT = 8080  // Cambia aquí
```

### Cambiar intervalo por defecto del slideshow
En `Prefs.kt`:
```kotlin
const val DEFAULT_INTERVAL_SECONDS = 10L  // Segundos
```

---

## Bloqueo de kiosk

ZenScreen implementa tres capas de bloqueo sin necesidad de root ni Device Owner:

| Capa | Descripción |
|------|-------------|
| **Botón Atrás** | Bloqueado — no hace nada |
| **Barras del sistema** | Ocultas — reaparecen brevemente al deslizar desde el borde y se vuelven a ocultar |
| **Botón Home** | Vuelve al kiosk si ZenScreen está configurado como launcher |

> Para bloqueo total (incluyendo barra de notificaciones) se requiere configurar la tablet como **Device Owner** via ADB. Ver [documentación oficial de Android](https://developer.android.com/work/dpc/build-dpc).

---

## Solución de problemas

### La app no arranca al encender
- Verifica que ZenScreen NO esté en la lista de apps optimizadas de batería
- Ajustes → Apps → ZenScreen → Batería → **Sin restricciones**

### El servidor WiFi no es accesible desde la PC
- Verifica que PC y tablet estén en la misma red WiFi
- Comprueba que la IP que muestra el panel admin sea la IP de la tablet
- Algunos routers bloquean la comunicación entre dispositivos (isolación de AP) — consulta la configuración de tu router

### Los PDFs no aparecen después de subirlos
- El kiosk recarga los archivos cada vez que `KioskActivity` entra en `onResume`
- Si no ocurre automáticamente: entra al panel admin y toca **Volver al kiosk**

### Build falla con error "defined multiple times"
macOS crea duplicados con espacio en el nombre al copiar carpetas. Solución:
```bash
find app/build -name "* 2.*" -delete
./gradlew clean assembleDebug
```

### El diálogo de PIN no aparece al hacer toque largo
- Asegúrate de mantener presionada la **esquina superior derecha** específicamente
- La zona sensible es un área de 80dp × 80dp en esa esquina

---

## Stack tecnológico

| Tecnología | Uso |
|-----------|-----|
| Kotlin | Lenguaje principal |
| Android PdfRenderer | Renderizado nativo de PDFs |
| NanoHTTPD 2.3.1 | Servidor HTTP embebido |
| Kotlinx Coroutines | Timer del slideshow y renders asíncronos |
| Material Components | UI del panel de administración |
| ViewBinding | Acceso a vistas |
| SharedPreferences | Persistencia de configuración |

---

## Contribuir

1. Fork del repositorio
2. Crea una rama: `git checkout -b feature/mi-mejora`
3. Commit: `git commit -m "feat: descripción del cambio"`
4. Push: `git push origin feature/mi-mejora`
5. Abre un Pull Request

---

## Licencia

MIT — libre para uso personal y comercial.
