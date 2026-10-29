# Limitaciones conocidas y solución de problemas

## Limitaciones

### Generales de Android

- **FocusGuard no puede impedir absolutamente el acceso a otra app.** Android no permite que una app
  común bloquee a otra. Lo que hace FocusGuard es reaccionar en cuanto la app aparece: abrir su
  pantalla de bloqueo encima (o ejecutar *Inicio*). Puede verse la app bloqueada durante una fracción
  de segundo.
- **Todo depende del servicio de accesibilidad.** Si lo desactivás, si Android lo detiene o si el
  fabricante lo mata por batería, no hay bloqueo ni conteo. El panel lo indica en rojo.
- **Se puede desactivar.** Cualquiera con el teléfono desbloqueado puede apagar el servicio o
  desinstalar la app. FocusGuard es una ayuda para tu autocontrol, no un control parental.
- **Cambios manuales de hora:** atrasar el reloj no regala aperturas en ventanas móviles, pero
  adelantarlo sí puede adelantar un reinicio diario o de bloque. Los cambios automáticos (red, zona
  horaria, horario de verano) se manejan correctamente.
- **Pantalla dividida y ventana flotante (PiP):** se considera en primer plano la app de la ventana
  activa. Un vídeo de YouTube en PiP mientras usás otra app no suma tiempo a YouTube.
- **Panel de notificaciones y Recientes:** bajar el panel no interrumpe el conteo de la app actual.
  En teléfonos donde Recientes es parte del launcher, abrir Recientes cuenta como salir de la app.
- **Primer evento tras iniciar el servicio:** la app que está en pantalla cuando el servicio se
  (re)inicia no se cuenta como apertura nueva (evita contar dos veces si Android reinicia el servicio).
- **Volver en menos de 5 s** a la misma app no cuenta como apertura nueva (evita dobles registros por
  diálogos o transiciones). Desbloquear el teléfono estando en la misma app tampoco.
- **Si borrás el historial con una app controlada abierta**, la sesión en curso se vuelve a guardar desde su inicio.
- **Solo se registra lo necesario:** las sesiones solo para apps con límite de tiempo activo y las
  aperturas solo para apps con límite de aperturas activo. Si activás un límite a mitad del día, el uso
  anterior de ese día no cuenta.

- **Después de actualizar la app**, Android a veces no vuelve a iniciar el servicio de accesibilidad
  (visto en el emulador). Con `instalar.bat` FocusGuard lo reactiva solo; si instalaste a mano, apagá y
  prendé el servicio en Accesibilidad.

### Modo mañana

- "Primer desbloqueo" lo detecta el servicio al desbloquear la pantalla: si el servicio no estaba
  funcionando en ese momento (p. ej. lo detuvo el ahorro de batería), ese día empieza con el siguiente
  desbloqueo o no se aplica.
- No cuentan para el límite de pantalla: las apps privilegiadas, el inicio (launcher), el teléfono, el
  reloj y FocusGuard. Esas apps esenciales nunca se bloquean, para no impedir llamadas ni alarmas.
- Como todo FocusGuard, se puede evitar desactivando el servicio de accesibilidad desde Ajustes de
  Android. Si querés que eso sea más difícil, no marques "Ajustes" como privilegiada… pero dejá siempre
  una salida de emergencia.

### Reels / Shorts

- **Instagram y YouTube cambian sus interfaces sin aviso.** La detección usa identificadores de vista
  conocidos (`clips_viewer_view_pager`, `reel_recycler`, etc.) y señales de pestaña seleccionada.
  Una actualización puede renombrarlos: la detección dejaría de funcionar (falso negativo). Se puede
  corregir sin recompilar agregando ids en **Ajustes → Ids extra**, o editando
  `app/src/main/java/com/personal/focusguard/detection/DetectionRules.kt`.
- **No se probó en un dispositivo real** durante el desarrollo (no hay dispositivo/emulador con esas
  apps en este entorno). Seguí [PRUEBAS_MANUALES.md](PRUEBAS_MANUALES.md).
- **Si una app no expone ids de vista** (algunas pantallas hechas con frameworks propios), la
  detección depende de la pestaña seleccionada + textos, que es menos fiable. FocusGuard prefiere no
  bloquear antes que bloquear toda la app.
- **Reels permitidos antes de salir:** los deslizamientos se cuentan con los eventos de desplazamiento
  del visor. Si una versión de Instagram/YouTube no los emite, el margen no se consume y la salida se
  produciría recién al reabrir el visor. No se pudo verificar con las apps reales (ver
  [RESULTADOS_EMULADOR.md](RESULTADOS_EMULADOR.md)). La cantidad es por vez: cada vez que entrás al
  visor (también desde la pestaña Reels) vuelve a empezar, así que no limita el total del día; para eso
  usá el límite de tiempo o de aperturas de la app.
- La detección tarda ~0,2–1 s en reaccionar, y analiza como máximo cada 0,6 s para cuidar la batería.
- **TikTok (experimental):** no expone ids estables, así que el visor se reconoce por estructura
  (una lista vertical desplazable que ocupa ≥ 88 % de la pantalla). En TikTok
  casi toda la app es el visor: con 0 permitidos te lleva a la Bandeja de entrada. Si una versión cambia
  esa estructura puede haber falsos negativos o positivos; el Diagnóstico muestra las listas detectadas
  (clase, alto, orientación) para ajustar.
- **Instagram Lite, YouTube Vanced/ReVanced, versiones web en el navegador** no están cubiertas
  (paquetes distintos). Los videos cortos de Facebook u otras apps tampoco.
- **X (ex Twitter) no está soportado:** no expone ids suficientes para distinguir su visor de videos
  cortos de forma fiable, así que se quitó para evitar bloqueos erróneos.

## Solución de problemas

| Síntoma | Qué hacer |
|---|---|
| El panel dice "Protección inactiva" | Ajustes de Android → Accesibilidad → FocusGuard → activar. |
| "Habilitado pero no en ejecución" | Si instalaste con `instalar.bat`, abrir FocusGuard lo reactiva solo. Si no, apagá y volvé a prender el servicio en Accesibilidad. Revisá la optimización de batería. |
| `instalar.bat` se queda "Esperando un celular conectado" | Revisá el cable (que sea de datos), que la depuración USB esté activa y aceptá el aviso en el celular. |
| No puedo activar el servicio (gris / "ajuste restringido") | Info de la app → ⋮ → *Permitir ajustes restringidos*. Ver [INSTALACION_Y_PERMISOS.md](INSTALACION_Y_PERMISOS.md). |
| Funciona un rato y deja de funcionar | Batería sin restricciones + inicio automático (ver guía por fabricante). Algunos teléfonos desactivan servicios de accesibilidad de apps no usadas: abrí FocusGuard de vez en cuando. |
| No detecta Reels/Shorts | Activá *Modo diagnóstico*, abrí el visor y revisá Diagnóstico: si `Señales: ninguna`, la interfaz cambió → agregá el id del visor en *Ids extra*. Verificá que *Detección automática* y el interruptor correspondiente estén activos. |
| Bloquea donde no debería (falso positivo) | Diagnóstico muestra qué señales coincidieron. Apagá temporalmente el interruptor de esa app y ajustá `DetectionRules.kt` (bajar el peso de la señal o agregar un id negativo). |
| Entra en bucle saliendo y entrando | No debería pasar más de 4 veces en 30 s: a partir de ahí se muestra la pantalla de bloqueo. Si pasa, apagá *Acción automática*. |
| El tiempo restante no coincide con el Bienestar digital de Android | Son cálculos distintos: FocusGuard cuenta solo con pantalla encendida y la app al frente, con su propia hora de reinicio. |
| El panel no se actualiza | Se refresca cada 30 s y con cada evento; salí y volvé a entrar. |
| Quiero empezar de cero | Ajustes → *Restablecer toda la configuración*, o desinstalar la app. |

## Actualizar las reglas de detección

1. Activá *Modo diagnóstico*.
2. Abrí el visor de Reels/Shorts con *Acción automática* y *Pantalla de bloqueo* apagadas.
3. En Diagnóstico copiá los ids visibles; compará con los que aparecen en el feed normal.
4. Un id que solo aparece en el visor → agregalo en *Ids extra* (suma 3 puntos si ocupa ≥ 50 % del alto).
5. Para cambios permanentes editá `DetectionRules.kt`, ejecutá `./gradlew testDebugUnitTest assembleDebug` y reinstalá.
