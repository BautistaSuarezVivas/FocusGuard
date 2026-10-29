# Instalación y permisos

Requisito: Android 8.0 o superior.

## Opción A — `instalar.bat` (recomendada: deja todo configurado)

Hace en un solo paso lo que de otro modo son varios menús de Android: instala la app por ADB, habilita
el servicio de accesibilidad, excluye la app de la optimización de batería y le otorga el permiso de
**reactivación automática** (si Android apaga el servicio, FocusGuard lo vuelve a encender solo).

1. En el celular, una sola vez: **Ajustes → Acerca del teléfono** → tocá 7 veces **Número de compilación**.
2. **Ajustes → Sistema → Opciones de desarrollador** → activá **Depuración por USB**.
   - Xiaomi/HyperOS: activá también **Depuración USB (ajustes de seguridad)** (necesario para la reactivación automática).
3. Conectá el celular a la PC por USB.
4. Doble clic en **`instalar.bat`** (en la carpeta del proyecto). Cuando el celular pregunte
   "¿Permitir depuración USB?", aceptá. El script muestra cada paso con OK.
5. Listo. Podés desconectar el cable y desactivar la depuración USB.

Si la PC no tiene ADB, el script ofrece descargarlo de Google (~7 MB). Sin cable: en Android 11+ podés
emparejar por **Depuración inalámbrica** (`adb pair`) y después ejecutar el script igual.

Para actualizar la app más adelante, volvé a ejecutar `instalar.bat` con el APK nuevo en `dist/`.

### Instalación manual por ADB (sin el script)

`adb` viene en las [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).

```powershell
adb install -r dist\FocusGuard-1.3.0.apk
```

Con ADB, Android 13+ normalmente no marca el servicio de accesibilidad como "ajuste restringido".

## Opción B — Copiar el APK al celular

1. Descargá `FocusGuard-1.3.0.apk` desde la sección **Releases** del repositorio y copialo al celular (cable, Drive, etc.).
2. Abrilo con el administrador de archivos. Android pedirá permitir **Instalar apps desconocidas**
   para esa app (administrador de archivos / navegador): permitilo solo para esta instalación.
3. Si Play Protect avisa que la app es desconocida, elegí **Instalar de todas formas** (es tu propia
   compilación de depuración, sin firma de Play Store).
4. En Android 13+ con esta opción probablemente tengas que habilitar los **ajustes restringidos**
   (ver más abajo).

## Primer inicio: asistente

Al abrir FocusGuard aparece un asistente de 4 pasos: funciones, privacidad, servicio de accesibilidad y batería.

## Habilitar el servicio de accesibilidad (imprescindible)

Si usaste `instalar.bat`, ya está hecho. Si no:

1. En el asistente o en **Ajustes** de FocusGuard → **Abrir Accesibilidad**.
2. FocusGuard aparece en **Apps instaladas** / **Servicios descargados** / **Aplicaciones descargadas**
   (el nombre varía). Android no permite que una app abra directamente su propio interruptor: la
   página de detalle exige un permiso de sistema (comprobado en Android 16).
3. Entrá a **FocusGuard – control de distracciones** → activá el interruptor → **Permitir**.
4. Volvé a FocusGuard: el panel tiene que mostrar **Protección activa**.

### Si aparece "Ajuste restringido" o el interruptor está gris (Android 13+)

1. **Ajustes → Apps → FocusGuard** (o el botón **Info de la app** dentro de FocusGuard).
2. Menú **⋮** (arriba a la derecha) → **Permitir ajustes restringidos** → confirmá con tu PIN/huella.
   En algunos teléfonos la opción solo aparece *después* de haber intentado activar el servicio una vez.
3. Volvé a Accesibilidad y activá el servicio.

### Qué hace FocusGuard con este acceso

- Saber qué app está en primer plano (para contar aperturas y tiempo de las apps que vos elegiste).
- Leer la *estructura* de la pantalla de Instagram y YouTube (ids de vista, etiquetas de botones)
  para reconocer los visores de Reels/Shorts. Los textos se comparan en memoria y se descartan.
- Ejecutar **Atrás** / **Inicio** y pulsar la pestaña **Inicio** de esas apps.

No guarda textos, mensajes, contraseñas ni capturas, y la app no tiene permiso de Internet.

## Batería (recomendado)

Algunos fabricantes detienen servicios en segundo plano.

- **Ajustes de FocusGuard → Batería** → buscá FocusGuard → **No optimizar** / **Sin restricciones**.
- **Xiaomi / HyperOS / MIUI:** Ajustes → Apps → FocusGuard → *Ahorro de batería: Sin restricciones*
  y *Inicio automático: activado*. Bloqueá la app en Recientes (candado).
- **Samsung:** Ajustes → Batería → *Límites de uso en segundo plano* → sacá FocusGuard de
  "Apps en suspensión" / "Suspensión profunda".
- **Huawei / Honor:** Inicio de aplicaciones → FocusGuard → gestión manual con todo activado.
- **Oppo / Realme / OnePlus:** Batería → FocusGuard → *Permitir actividad en segundo plano*.

Más ayuda por fabricante: <https://dontkillmyapp.com>.

## Permisos que FocusGuard NO pide (y por qué)

| Acceso | Motivo para no usarlo |
|---|---|
| Acceso a datos de uso | El servicio de accesibilidad ya informa qué app está al frente, en tiempo real. |
| Notificaciones | Los avisos de "te quedan X min" usan mensajes breves (toast), que no requieren permiso. |
| Alarmas exactas | Los períodos se recalculan con cada evento; no hay que "despertar" a nadie para reiniciar contadores. |
| Superposición sobre otras apps | La pantalla de bloqueo es una actividad propia que el servicio de accesibilidad puede abrir. |
| Inicio tras el arranque | Android reconecta solo los servicios de accesibilidad habilitados al encender el equipo. |
| Internet | Todo es local. |

El único permiso "avanzado" es **WRITE_SECURE_SETTINGS**, opcional y solo otorgable desde una PC
(lo hace `instalar.bat`). FocusGuard lo usa exclusivamente para volver a encender **su propio**
servicio de accesibilidad; los demás servicios que tengas habilitados se conservan. Se puede desactivar
en Ajustes de FocusGuard → *Reactivar el servicio automáticamente*, o revocar con
`adb shell pm revoke com.personal.focusguard android.permission.WRITE_SECURE_SETTINGS`.

## Verificar permisos más adelante

**FocusGuard → ⚙ Ajustes → Permisos y estado** muestra:

- Servicio habilitado en Ajustes de Android.
- Servicio realmente en ejecución (si figura habilitado pero no en ejecución, apagalo y prendelo).
- Exclusión de la optimización de batería.

El panel principal también avisa en rojo si la protección está inactiva.

## Compilar el APK vos mismo

Ver [README](../README.md#compilar).
