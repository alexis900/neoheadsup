# NeoHeadsUp

**Stay aware while walking.**

NeoHeadsUp es una aplicación Android ligera que te recuerda levantar la vista cuando
caminas mientras usas el teléfono. Está inspirada en la función *Heads Up* de Digital
Wellbeing, pero es una implementación independiente: no contiene código, recursos ni marcas
de Google y no está afiliada a Google.

- **Nombre visible:** NeoHeadsUp
- **Repositorio:** `neoheadsup`
- **Tecnología principal:** Kotlin
- **Plataforma:** Android
- **Idioma principal:** español (con traducción al inglés)

## Objetivo

Reducir el riesgo de caminar mirando la pantalla sin introducir permisos invasivos,
localización, servicios de accesibilidad, overlays ni avisos a pantalla completa. La app
prioriza privacidad, batería y simplicidad: todo el estado se guarda en el dispositivo y no
existe permiso de Internet.

## Funcionalidades actuales

- Detección de entrada y salida de la actividad `WALKING` mediante la
  **Activity Recognition Transition API** de Google Play Services.
- **Confirmación de caminata de 15 segundos:** una transición `ENTER` aislada no genera
  ningún aviso. Si llega una salida durante la ventana, la confirmación se cancela.
- **Confirmación de parada de 10 segundos:** una salida breve tampoco cierra la sesión; si
  vuelve a entrar actividad de caminata, la parada se cancela.
- **Enfriamiento configurable** (5, 10, 15 o 30 minutos; 15 por defecto) entre recordatorios.
- **Caducidad por falta de evidencia:** una sesión de caminata sin evidencia reciente se
  descarta automáticamente a los 10 minutos. Nunca se mantiene viva artificialmente.
- **Rechazo de eventos duplicados y fuera de orden** usando el reloj monotónico de los
  eventos de actividad.
- **Recuperación tras reinicio:** un reinicio invalida el orden de los eventos y borra la
  sesión anterior. Una actualización de la app conserva la sesión porque el reloj del
  dispositivo no cambia.
- **Rechequeo al desbloquear y periódico** mientras hay una sesión activa, para mostrar el
  recordatorio en cuanto la pantalla esté encendida y desbloqueada.
- **Diagnóstico:** cada transición, tick y decisión de aviso se registra con fecha, actividad,
  estado anterior y nuevo, motivo de aceptación/rechazo y motivo de la decisión del aviso.
  El historial completo está en Logcat con la etiqueta `NeoHeadsUp` y el último evento se
  muestra en la interfaz.
- Acciones de notificación: **No estoy caminando** (snooze de 30 minutos) y **Desactivar**.
- Recordatorio de prueba manual desde la interfaz.

## Tecnologías utilizadas

- Kotlin 1.9.
- Android SDK 34, mínimo Android 10 (API 29).
- Vistas del framework Android (XML) y `SharedPreferences`.
- `AlarmManager` y un chequeo en proceso con `Handler` para las comprobaciones de
  confirmación, parada y caducidad. La alarma actúa como respaldo si el proceso se cierra.
- Google Play Services Location (`ActivityRecognition`).
- Gradle 8.7 con Android Gradle Plugin 8.5.2.
- JUnit 4 para las pruebas unitarias de la lógica pura.

> **Nota sobre Jetpack Compose:** el proyecto existente no usaba Compose; su interfaz está
> construida con vistas XML. Para no reconstruir la aplicación sin justificación se mantuvo
> esa arquitectura y se añadió un núcleo de detección puro y testeable. Migrar a Compose
> sería un cambio independiente y no forma parte de esta corrección.

## Arquitectura

La lógica de decisión es Kotlin puro y no depende de Android:

- `detection/SessionConfig.kt`: ventanas de confirmación, caducidad y valores por defecto.
- `detection/SessionState.kt`: estados `OFF`, `IDLE` y `WALKING`.
- `detection/SessionSnapshot.kt`: estado persistible de la sesión.
- `detection/DetectionEvent.kt`: transición normalizada con reloj de pared y monotónico.
- `detection/SessionController.kt`: máquina de estados y cálculo del próximo chequeo.
- `detection/AlertDecider.kt`: política pura que decide si se puede mostrar un aviso.

La capa Android conecta ese núcleo con el sistema:

- `WalkingTransitionReceiver`: recibe las transiciones de Play Services.
- `DetectionEngine`: carga/guarda el estado, programa alarmas y escribe diagnóstico.
- `SessionStore`: persistencia privada del snapshot.
- `RecheckReceiver` y `RecheckScheduler`: confirmación, parada, caducidad y rechequeos. El
  `Handler` en proceso reduce el retraso de las alarmas inexactas; `AlarmManager` queda como
  respaldo si Android cierra el proceso.
- `UserPresentReceiver`: comprobación al desbloquear.
- `BootReceiver`: recuperación tras reinicio o actualización.
- `NotificationActionReceiver`: snooze y desactivación.
- `HeadsUpNotifier`: aplica `AlertDecider` y publica la notificación.
- `Diag`: registro en Logcat y último evento visible en la app.

### Parámetros de detección

| Parámetro | Valor |
|---|---:|
| Confirmación de caminata | 15 s |
| Confirmación de parada | 10 s |
| Enfriamiento por defecto | 15 min |
| Caducidad sin evidencia | 10 min |
| Rechequeo mientras se camina | 60 s |
| Timeout de confirmación pendiente | 10 min |

## Requisitos de compilación

- JDK 17.
- Android SDK Platform 34 y Build Tools instalados.
- `ANDROID_HOME` o `local.properties` apuntando al SDK (este último no se versiona).
- Dispositivo o emulador con Google Play Services para probar la detección real.

## Compilar e instalar

```bash
export JAVA_HOME=/ruta/al/jdk17
export ANDROID_HOME=/ruta/a/Android/Sdk
./gradlew assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Con un dispositivo conectado y la depuración USB activada:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

También puedes copiar el APK al teléfono y abrirlo, permitiendo la instalación de apps de
origen desconocido si el sistema lo solicita.

## Primer uso

1. Abre NeoHeadsUp.
2. Concede el permiso de **Actividad física** y, en Android 13 o superior, el de
   **Notificaciones**.
3. Activa el interruptor.
4. Elige opcionalmente la frecuencia de repetición.
5. Camina con el teléfono en la mano y la pantalla encendida y desbloqueada. El recordatorio
   aparecerá tras 15 segundos de caminata continua confirmada.
6. Usa **Enviar recordatorio de prueba** para comprobar el estilo de la notificación sin
   caminar.

## Permisos y justificación

| Permiso | Motivo |
|---|---|
| `ACTIVITY_RECOGNITION` | Detectar la transición a caminata mediante Play Services. |
| `com.google.android.gms.permission.ACTIVITY_RECOGNITION` | Compatibilidad con versiones antiguas de Play Services. |
| `POST_NOTIFICATIONS` | Mostrar el recordatorio en Android 13 o superior. |
| `RECEIVE_BOOT_COMPLETED` | Volver a registrar la detección después de reiniciar. |

La app **no** declara `INTERNET`, no usa GPS ni ubicación, no usa servicios de accesibilidad,
no dibuja overlays y no muestra avisos a pantalla completa. Las copias de seguridad están
desactivadas.

## Pruebas

Pruebas unitarias de la lógica de detección:

```bash
./gradlew testDebugUnitTest
```

Cubren los nueve escenarios solicitados:

1. Usuario sentado sin actividad previa.
2. Usuario sentado después de caminar.
3. Inicio de caminata.
4. Finalización de caminata.
5. Eventos duplicados.
6. Eventos fuera de orden.
7. Estados persistidos obsoletos.
8. Reinicio del proceso y reinicio del dispositivo.
9. Avisos sin evidencia reciente.

Además se comprueban enfriamiento, snooze, pantalla bloqueada, función desactivada y
transiciones rápidas de salida y vuelta.

Prueba de humo en dispositivo (requiere pantalla encendida y desbloqueada):

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  io.alexis900.neoheadsup.test/io.alexis900.neoheadsup.WalkingTestRunner
```

Resultado en Pixel 7a (transiciones sintéticas): `immediateNotification=false`,
`walkingConfirmed=true`, `notified=true`, `confirmationLatencyMs=15083`,
`stopLatencyMs=10090`, `timingOk=true`, `exitClosed=true`. La prueba también falla si las
ventanas de 15 s y 10 s se alejan del margen esperado. No sustituye la validación con
movimiento real.

### Pruebas físicas en Pixel 7a

Estado: **validación física principal superada; quedan comprobaciones secundarias**. La
instalación, los permisos, el canal de notificación, el renombrado del launcher y la prueba de
humo instrumentada ya se comprobaron. Además, la validación con movimiento real confirmó la
ventana de 15 s y la parada de 10 s:

- Línea base de 90 s sentado: sin transiciones `WALKING`, sin avisos y estado `IDLE`.
- Caminata real (`00:18:02.828`–`00:19:09.968`): `ENTER` → `WALKING` a los 15,007 s, aviso
  permitido, `EXIT` → `IDLE` a los 10,004 s.
- Durante la misma caminata no se repitió el aviso: `allowed=false reason=cooldown`.
- Botones **No estoy caminando** y **Desactivar** probados con la notificación de prueba;
  snooze, desactivación y reactivación funcionaron.

Lista de comprobaciones:

- [x] Instalar la nueva app (`io.alexis900.neoheadsup`) y comprobar que el launcher muestra
      NeoHeadsUp (puede convivir con la versión anterior hasta desinstalarla).
- [x] Permanecer sentado quieto durante varios minutos y confirmar que no aparece ningún aviso.
- [x] Caminar de verdad con la pantalla encendida y desbloqueada: el aviso debe aparecer tras
      unos 15 segundos, no de inmediato.
- [ ] Entrar y salir rápidamente de la actividad de caminata: la confirmación de 15 s debe
      reiniciarse y no debe generarse un aviso falso.
- [x] Caminar, detenerse y permanecer sentado: el estado debe volver a `IDLE` tras 10 s y no
      deben quedar avisos pendientes.
- [ ] Bloquear el teléfono mientras se camina y desbloquearlo después: el aviso debe aparecer
      solo si la evidencia sigue siendo reciente.
- [ ] Comprobar el enfriamiento de 15 minutos entre dos caminatas distintas. Ya se observó un
      rechazo por `cooldown` dentro de la misma caminata.
- [x] Probar los botones **No estoy caminando** y **Desactivar** (con notificación de prueba).
- [ ] Reiniciar el dispositivo y confirmar que la sesión anterior no se reutiliza.
- [x] Revisar `adb logcat -s NeoHeadsUp` y verificar los motivos de aceptación, rechazo y
      decisión del aviso.

## Compatibilidad

- Android 10 (API 29) o superior.
- Android 13+ solicita además el permiso de notificaciones en tiempo de ejecución.
- Requiere Google Play Services para la detección de actividad.

## Identidad y paquete técnico

El nombre visible, el launcher, la interfaz, la documentación, el repositorio y el paquete
técnico usan **NeoHeadsUp**:

- `applicationId`: `io.alexis900.neoheadsup`
- `namespace`: `io.alexis900.neoheadsup`
- Paquete Kotlin: `io.alexis900.neoheadsup`

Consecuencia del cambio de `applicationId`: el sistema considera NeoHeadsUp una aplicación
distinta de la versión anterior (`com.amartinper.miraalfrente`). Los ajustes antiguos no se
migran, la app anterior debe desinstalarse manualmente y durante la transición podrían
aparecer dos iconos. A cambio, la identidad técnica queda alineada con el nombre público.


## Limitaciones conocidas

- La precisión depende de Google Play Services y del sensor de actividad del dispositivo. La
  API puede omitir transiciones o notificar actividad incorrecta; por eso la app exige
  evidencia continua y caduca las sesiones sin evidencia reciente.
- Si el proceso se fuerza a detener o el fabricante restringe el trabajo en segundo plano,
  las alarmas de confirmación pueden no ejecutarse.
- Las alarmas no son exactas: `AlarmManager` puede retrasar la confirmación. Para reducirlo,
  el chequeo también se programa con un `Handler` en proceso; si Android cierra el proceso,
  solo queda el respaldo de la alarma y el aviso puede retrasarse.
- El aviso solo se muestra con la pantalla encendida y desbloqueada. Nunca se usa un overlay
  ni una notificación a pantalla completa.
- Tras 10 minutos de caminata continua sin nuevas transiciones, la sesión caduca por
  seguridad y no se generan más avisos hasta que Play Services vuelva a notificar una entrada.
- La app todavía no se ha validado físicamente en todos los escenarios del Pixel 7a; consulta
  la lista anterior.

## Privacidad

Todo el estado se guarda en `SharedPreferences` privadas del dispositivo. No hay backend, no
hay permiso de Internet y no se envía información a ningún servidor. Los diagnósticos solo se
escriben en el registro local y en la propia app.

## Estado de desarrollo

Versión inicial de NeoHeadsUp. La lógica de detección está cubierta por pruebas unitarias y la
prueba instrumental; el APK `io.alexis900.neoheadsup` está instalado en el Pixel 7a. La
validación física con movimiento real confirmó el aviso a los 15 s y el cierre de la sesión a
los 10 s. Quedan pendientes las comprobaciones secundarias de bloqueo, enfriamiento entre dos
caminatas, transiciones rápidas y reinicio del dispositivo.

## Licencia

No se ha añadido ninguna licencia. Todos los derechos quedan reservados hasta que se decida
una licencia de forma expresa.
