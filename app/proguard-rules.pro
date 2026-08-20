# NanoHTTPD usa carga de recursos y logging que R8 no siempre detecta
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Receivers y servicios declarados en el manifest (R8 los conserva por defecto,
# pero explícito por seguridad con los componentes del modo kiosk)
-keep class com.zenscreen.app.KioskDeviceAdminReceiver { *; }
-keep class com.zenscreen.app.BootReceiver { *; }

# Conservar nombres de línea para stacktraces legibles en Play Console
-keepattributes SourceFile,LineNumberTable
