# Publicación en tiendas y versión iOS (plan, sin implementar)

Estado al 23/10/2026: decidido **solo Android, uso personal**. Este documento resume qué haría falta
para publicar en Google Play y qué sería posible en iOS, para retomarlo más adelante.

## Google Play (Android)

Viable, con estos pasos:

| # | Tarea | Notas |
|---|---|---|
| 1 | **Aviso destacado de accesibilidad** dentro de la app, aceptado por el usuario antes de activar el servicio | Obligatorio porque FocusGuard no es una herramienta para personas con discapacidad (`isAccessibilityTool="false"`). Es el mayor riesgo de rechazo en la revisión. |
| 2 | Declaración del uso de la API de accesibilidad en Play Console + **video** de demostración | |
| 3 | **Variante "Play"** sin `WRITE_SECURE_SETTINGS`, sin reactivación automática y sin referencias a `instalar.bat` | Ese permiso solo se otorga por ADB; en Play no aplica. La variante "personal" actual se conserva. |
| 4 | **Firma propia** (keystore de subida + Play App Signing) y compilación **AAB** | Hoy el release se firma con la clave de depuración local. |
| 5 | **Política de privacidad** publicada en una URL y formulario de **seguridad de datos** | FocusGuard no recolecta ni envía datos. |
| 6 | Ficha de la tienda: textos, capturas, ícono 512 px, gráfico destacado | Se puede mencionar Instagram/YouTube/TikTok para describir la función, sin usar sus logos. |
| 7 | Cuenta de desarrollador (USD 25, verificación de identidad) | Cuentas personales nuevas: **prueba cerrada con 12 testers durante 14 días** antes de producción. |
| 8 | **Nombre e identificador definitivos** | `com.personal.focusguard` no se puede cambiar después de publicar; verificar que "FocusGuard" esté libre. |

## iOS

**No es posible bloquear Reels/Shorts dentro de las apps de Instagram, YouTube o TikTok**: iOS no
permite que una app lea la pantalla de otra ni actúe sobre ella (no existe un equivalente al servicio
de accesibilidad de Android).

Con la API de **Tiempo en Pantalla** (FamilyControls, ManagedSettings, DeviceActivity) sí se podría:

- Límites de tiempo por app y bloqueo por horario, con pantalla de bloqueo personalizada.
- "Desbloquear N minutos".
- Límite de aperturas, de forma parcial: con una automatización de Atajos ("al abrir Instagram → FocusGuard")
  que el usuario crea a mano una vez por app.
- Opcional: extensión de Safari para bloquear Reels/Shorts en las **versiones web**.

Sería una app aparte en Swift/SwiftUI (casi nada del código Android se reutiliza). Requisitos:

- Mac con Xcode (o Mac en la nube: GitHub Actions, Codemagic).
- Apple Developer Program (USD 99/año) e iPhone real para probar.
- Aprobación de Apple del permiso **Family Controls (Distribution)** para publicar; en 2026 hay
  reportes de esperas de semanas o meses.

## Fuentes

- [Política de AccessibilityService de Google Play](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Requisito de 12 testers (resumen de terceros)](https://www.testerscommunity.com/blog/google-play-closed-testing-requirements-2026)
- [Configuración de Family Controls (Apple)](https://developer.apple.com/documentation/xcode/configuring-family-controls.md)
- [Demoras del permiso Family Controls en 2026 (foro de Apple)](https://developer.apple.com/forums/thread/818553)
