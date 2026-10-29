# Resultados en emulador

Entorno: emulador Android 16 (API 36, Google Play, Pixel 7, x86_64 con WHPX), FocusGuard 1.3.0.
Las decisiones se verificaron con los registros `FocusGuard` de logcat y con capturas de pantalla.

## Verificado ✓

| Prueba | Resultado |
|---|---|
| Asistente inicial, panel, selector de apps, ficha de reglas | Funcionan; reglas guardadas y mostradas con colores distintos (tiempo / aperturas). |
| Servicio de accesibilidad | Se enlaza y recibe los cambios de app en primer plano. |
| **Aperturas** (Reloj: 2 en ventana móvil de 2 min) | 1.ª y 2.ª permitidas; la navegación interna no generó eventos; volver en < 5 s no contó; la 3.ª y la 4.ª se bloquearon sin consumir aperturas; la pantalla de bloqueo apareció en 0,4 s con la hora correcta; pasada la ventana se volvió a permitir. |
| **Tiempo** (Calendario: 1 min diario) | El tiempo en Chrome no se sumó a Calendario; con 12,33 s restantes bloqueó a los 12,59 s; el reintento se bloqueó al instante; reinicio "mañana 04:00". |
| **Reinicio del dispositivo** | El servicio volvió a conectarse solo y Calendario siguió bloqueado (estado recuperado de la base de datos). |
| Pantalla de bloqueo en frío tras reiniciar | 2,7 s con APK debug → **0,47 s con APK release** (por eso el release es el recomendado). |
| YouTube: pestaña Shorts seleccionada **sin** visor (pantalla de error) | No bloqueó (correcto: la pestaña sola no alcanza). |
| `instalar.bat` desde cero | Instaló, otorgó WRITE_SECURE_SETTINGS, habilitó el servicio (enlazado), excluyó de batería y abrió la app. |
| Reactivación automática | Con el servicio apagado, abrir FocusGuard lo reactivó; tras actualizar el APK quedó enlazado. |
| Tema claro / oscuro / según el sistema | Los tres funcionan; las barras del sistema se adaptan. |
| Ajustes por secciones (5 menús) y panel con 4 plataformas | Funcionan; el aviso de batería ya no aparece en el panel. (Instagram y YouTube usan las mismas claves de ajustes que la 1.1, así que deberían conservarse al actualizar; no se probó con valores distintos de los predeterminados.) |

### Visores de videos cortos con apps de prueba (`fixtures/fakeapps`)

| Escenario | Resultado |
|---|---|
| A. Instagram de prueba, 0 permitidos, abrir el visor | Detectado por id (`clips_viewer_view_pager`, puntaje 4) → Atrás → salida verificada en < 1 s. |
| B. 1 reel permitido, **por vez** | 3 visitas seguidas saliendo uno mismo: en las 3 se pudo ver el reel. En la 4.ª, al deslizar, salió. En la 5.ª (inmediatamente después) se volvió a permitir. |
| C. Acción automática apagada → pantalla de bloqueo | **Primero sale del visor** (a los 0,8 s ya estaba en la pantalla segura) y luego muestra el bloqueo; al volver a abrir la app queda en la pantalla segura, no en el visor. Atrás en la pantalla de bloqueo lleva al inicio. |
| D. TikTok de prueba | La bandeja de entrada no se bloquea; el visor se detecta por estructura (`class:pager_fullscreen`) y sale con Atrás. |

Las apps de prueba imitan la estructura, no el contenido real: confirman que el servicio, la
detección, los deslizamientos y las acciones funcionan, pero no que los ids/estructuras de las apps
reales sigan siendo los esperados.

### Modo mañana

Configuración: empieza al primer desbloqueo después de las 05:00, dura 2 h, límite de pantalla 1 min,
Instagram de prueba restringida, Calendario privilegiada. Se simuló despertarse (apagar, encender y
desbloquear a las 06:48).

| Caso | Resultado |
|---|---|
| App restringida | Bloqueada al instante, "se habilita" a las 08:48 (2 h desde el desbloqueo). |
| App privilegiada (Calendario) 70 s | Permitida, y su tiempo no contó para el límite. |
| App no privilegiada (Chrome) | Bloqueada a los 60,3 s ("solo podés usar tus apps privilegiadas"). |
| App esencial (Reloj) con el límite agotado | Permitida. |
| Chrome otra vez | Bloqueada de inmediato. |
| Panel | Tarjeta "Modo mañana activo · hasta 08:49 · pantalla agotada". |

## Errores encontrados en el emulador y corregidos

1. **Campos numéricos:** borrar rápido "60" y escribir "2" dejaba "26" (el campo restauraba un valor
   intermedio y movía el cursor). Corregido y verificado.
2. **Diálogos de Google Play Services** (selector de cuenta, etc.) se tomaban como "otra app" y
   cortaban el conteo de tiempo de la app de abajo. Ahora se ignoran como el teclado o la barra del sistema.
3. **Hora mostrada truncada:** "la próxima apertura se habilita 05:02" cuando era 05:02:21. Ahora se
   redondea hacia arriba (05:03).
4. **Acceso directo al interruptor del servicio:** Android 16 lo rechaza ("Permission Denial") para
   apps comunes. Se reemplazó por abrir Accesibilidad con FocusGuard listado arriba.

## No verificado (requiere tu cuenta)

- **Detección con las apps reales** de Instagram, YouTube y TikTok.
  El YouTube preinstalado (19.17, de 2024) ahora exige actualizarse desde Play Store, y las otras
  apps no vienen instaladas. Todas requieren iniciar sesión en Play Store dentro del emulador (yo no
  puedo ingresar contraseñas). Con una sesión iniciada se puede actualizar YouTube, instalar las demás
  y repetir [PRUEBAS_MANUALES.md](PRUEBAS_MANUALES.md) secciones 1–5c.

Nota técnica: las herramientas de prueba basadas en `uiautomator` desconectan temporalmente los
servicios de accesibilidad; por eso las mediciones se hicieron sin ellas. No afecta el uso normal.
