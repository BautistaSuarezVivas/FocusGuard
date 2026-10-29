# Pruebas manuales en el dispositivo real

Que el proyecto compile y pasen los tests unitarios **no** prueba que la detección de Reels/Shorts
funcione con tus versiones de Instagram y YouTube. Hacé esta lista después de instalar y anotá
el resultado.

Preparación:

1. Servicio de accesibilidad activo (panel: **Protección activa**).
2. **Ajustes → Modo diagnóstico: activado** (solo mientras probás).
3. Anotá las versiones: Ajustes → Apps → Instagram / YouTube → versión.

Cómo leer el diagnóstico: después de cada paso volvé a FocusGuard → Ajustes → **Abrir diagnóstico**.
Muestra el puntaje, las señales que coincidieron, la última acción y los ids visibles.

## 1. Instagram — no debe bloquear

| # | Acción | Resultado esperado | OK |
|---|---|---|---|
| I1 | Abrir Instagram en el feed y hacer scroll 1 min (incluyendo reels incrustados en el feed) | No pasa nada. Diagnóstico: `detectado = no` | ☐ |
| I2 | Abrir Mensajes (DM) y una conversación | No pasa nada | ☐ |
| I3 | Abrir tu perfil y el de otra cuenta; tocar la pestaña de Reels del perfil (cuadrícula) | No pasa nada (la cuadrícula no es el visor) | ☐ |
| I4 | Abrir una publicación de foto y una de carrusel | No pasa nada | ☐ |
| I5 | Abrir Historias | No pasa nada | ☐ |
| I6 | Buscar / Explorar (cuadrícula) | No pasa nada | ☐ |

## 2. Instagram — debe bloquear

| # | Acción | Resultado esperado | OK |
|---|---|---|---|
| I7 | Tocar la pestaña **Reels** de la barra inferior | En ≤ 1–2 s vuelve atrás (normalmente al feed). Diagnóstico: `detectado = sí`, acción "Atrás" y "salida verificada" | ☐ |
| I8 | En el feed, tocar un reel para abrirlo a pantalla completa | Sale del visor y vuelve al feed | ☐ |
| I9 | Abrir un reel enviado por DM | Sale y vuelve a la conversación | ☐ |
| I10 | Abrir un reel desde la cuadrícula de un perfil | Sale y vuelve al perfil | ☐ |
| I11 | Repetir I7 cinco veces seguidas rápidamente | No queda en bucle; a la 5.ª detección en 30 s aparece la pantalla de bloqueo | ☐ |

Si I7–I10 fallan: abrí el visor, volvé a FocusGuard y mirá en Diagnóstico los **ids visibles** de esa
pantalla (la última evaluación se conserva). Buscá un id que aparezca solo en el visor y no en el feed
(p. ej. algo con `clips` o `reel` en el nombre) y agregalo en **Ajustes → Ids extra de Instagram**.
Lo mismo vale para YouTube.

## 3. YouTube — no debe bloquear

| # | Acción | Resultado esperado | OK |
|---|---|---|---|
| Y1 | Inicio con el estante de Shorts visible, scroll | No pasa nada | ☐ |
| Y2 | Reproducir un vídeo normal (vertical y horizontal), pantalla completa | No pasa nada | ☐ |
| Y3 | Buscar algo; ver resultados que incluyan Shorts | No pasa nada | ☐ |
| Y4 | Página de un canal → pestaña **Shorts** (cuadrícula) | No pasa nada | ☐ |
| Y5 | Suscripciones, Biblioteca/Tú, comentarios de un vídeo | No pasa nada | ☐ |

## 4. YouTube — debe bloquear

| # | Acción | Resultado esperado | OK |
|---|---|---|---|
| Y6 | Tocar la pestaña **Shorts** de la barra inferior | Sale del visor (vuelve a Inicio) | ☐ |
| Y7 | Tocar un Short del estante de Inicio | Sale y vuelve a Inicio | ☐ |
| Y8 | Abrir un enlace `youtube.com/shorts/...` desde otra app | Sale del visor o aparece la pantalla de bloqueo | ☐ |
| Y9 | Desde un canal → Shorts → tocar un Short | Sale del visor | ☐ |

## 5. Interruptores

| # | Acción | Esperado | OK |
|---|---|---|---|
| T1 | Apagar *Bloquear Reels* → I7 | No bloquea Reels; Shorts sigue bloqueado | ☐ |
| T2 | Apagar *Acción automática* (pantalla de bloqueo encendida) → I7 | Aparece directamente la pantalla de bloqueo | ☐ |
| T3 | Apagar *Acción automática* y *Pantalla de bloqueo* → I7 | Solo un aviso "Reels detectado" | ☐ |
| T4 | Apagar *Detección automática* → I7 / Y6 | Nada se bloquea | ☐ |

## 5b. Reels/Shorts permitidos

Ajustes → *Reels permitidos antes de salir* = 1 (y lo mismo para Shorts).

| # | Acción | Esperado | OK |
|---|---|---|---|
| P1 | Abrir un reel enviado por DM y mirarlo entero | No te saca. Diagnóstico: acción "permitido: 1 de 1" | ☐ |
| P2 | Deslizar al siguiente reel | Te saca (vuelve a la conversación) | ☐ |
| P3 | Ver un reel enviado, salir a responder y abrir otro reel enviado | El segundo también se puede ver (la cantidad es por vez) | ☐ |
| P4 | Después de que FocusGuard te saque, abrir otro reel enviado | Se puede ver | ☐ |
| P5 | Abrir comentarios del reel y deslizar dentro de ellos | No cuenta como reel nuevo | ☐ |
| P6 | Repetir P1–P2 con un Short de YouTube | Mismo comportamiento | ☐ |

## 5c. TikTok

| # | Acción | Esperado | OK |
|---|---|---|---|
| K1 | Abrir TikTok (feed "Para ti") | Te lleva a la Bandeja de entrada (o al bloqueo) | ☐ |
| K2 | Ir a Bandeja de entrada / Perfil | No pasa nada | ☐ |
| K3 | Con 1 permitido: abrir un TikTok enviado por WhatsApp, mirarlo y deslizar | Lo ves; al deslizar te saca | ☐ |

## 5d. Modo mañana

Configurar: empieza al primer desbloqueo, límite de pantalla 2 min, una app restringida y una privilegiada.

| # | Acción | Esperado | OK |
|---|---|---|---|
| M1 | A la noche, antes de dormir, usar el teléfono normalmente | Sin restricciones | ☐ |
| M2 | A la mañana, primer desbloqueo; abrir la app restringida | Bloqueada, con la hora de fin | ☐ |
| M3 | Usar la app privilegiada varios minutos | Permitida; no consume el límite | ☐ |
| M4 | Usar otra app 2 min | Se bloquea; solo quedan las privilegiadas | ☐ |
| M5 | Sonar una alarma / hacer una llamada | Nunca se bloquea | ☐ |
| M6 | Pasada la duración | Todo vuelve a la normalidad | ☐ |

## 6. Límite de tiempo

Crear regla: Instagram, 2 min, diario, reinicio global 04:00.

| # | Acción | Esperado | OK |
|---|---|---|---|
| L1 | Usar Instagram 1 min, salir; el panel muestra ~1 min restante | Correcto | ☐ |
| L2 | Abrir WhatsApp 2 min | El tiempo de Instagram no cambia | ☐ |
| L3 | Volver a Instagram y quedarse | A los ~2 min acumulados aparece la pantalla de bloqueo con "se restablece mañana 04:00" (aviso previo de 1 min) | ☐ |
| L4 | Intentar abrir Instagram otra vez | Bloqueado de inmediato | ☐ |
| L5 | Con la pantalla apagada 5 min dentro de Instagram (antes del límite) | Ese tiempo no se cuenta | ☐ |
| L6 | Reiniciar el teléfono y abrir Instagram | Sigue bloqueado / el tiempo usado se conserva | ☐ |
| L7 | Cambiar la hora de reinicio a 1 min en el futuro y esperar | Se habilita Instagram de nuevo | ☐ |

## 7. Límite de aperturas

Crear regla: Instagram, 2 aperturas, ventana móvil 5 min.

| # | Acción | Esperado | OK |
|---|---|---|---|
| A1 | Abrir Instagram, cambiar de pantallas dentro, salir al inicio | Cuenta 1 apertura | ☐ |
| A2 | Abrir y salir de nuevo (esperar > 5 s fuera) | Cuenta 2 | ☐ |
| A3 | Tercer intento | Pantalla de bloqueo con la hora de la próxima apertura; el contador sigue en 2 | ☐ |
| A4 | Esperar hasta la hora indicada y abrir | Se permite | ☐ |
| A5 | Bajar la barra de notificaciones / abrir el teclado dentro de Instagram | No cuenta aperturas nuevas | ☐ |
| A6 | Cambiar a bloques fijos de 5 min y repetir A1–A3 | El contador vuelve a 0 al empezar cada bloque | ☐ |
| A7 | Reiniciar el teléfono | Los contadores se conservan | ☐ |

## 8. Robustez

| # | Acción | Esperado | OK |
|---|---|---|---|
| R1 | Cambiar la zona horaria del teléfono | El "próximo reinicio" se recalcula con la hora local nueva | ☐ |
| R2 | Dejar el teléfono 1 hora en reposo y volver a usar Instagram | El servicio sigue funcionando | ☐ |
| R3 | Ajustes → Borrar historial | Los contadores vuelven a 0 | ☐ |

Al terminar, apagá el **Modo diagnóstico**.
