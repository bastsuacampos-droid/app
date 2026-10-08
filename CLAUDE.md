# Charlas Ruta Itata

App Android (Kotlin + Jetpack Compose) con el banco de charlas de seguridad de 5 minutos
del proyecto *Mejoramiento de la Ruta Itata* (Sacyr). La usan capataces y supervisores en
terreno. Todo el contenido y la interfaz están en español de Chile: escribe en ese idioma.

## Comandos

```bash
./gradlew testDebugUnitTest     # pruebas unitarias (estructura del banco, planificador, recomendador…)
./gradlew assembleDebug         # APK de prueba: app/build/outputs/apk/debug/app-debug.apk
python3 tools/generar_banco_md.py   # regenera docs/BANCO_CHARLAS.md (obligatorio si cambia charlas.json)
```

Requisitos locales: JDK 17+, Android SDK con plataforma 35 (`local.properties` con `sdk.dir`,
no se versiona). La APK de publicación se firma solo si existen las variables
`FIRMA_KEYSTORE` y `FIRMA_CLAVE` (ver "Publicar una versión").

## Estructura

- `app/src/main/assets/charlas.json`: **todo el contenido**. Especialidades, charlas, plan
  sugerido de 4 semanas (`plan`) y actividades para el recomendador (`actividades`).
  Los teléfonos lo descargan desde `main` (raw.githubusercontent) y lo aplican si su
  `version` es mayor y pasa `BancoParser.validar`.
- `app/src/main/java/cl/sacyr/rutaitata/charlas/data/`: lógica sin interfaz y con pruebas.
  - `BancoParser` (lectura y validación), `Modelo` (Banco, Charla, Actividad…).
  - `Contenido` (descarga del banco), `ActualizacionApp` (busca releases `charlas-v*`,
    descarga e instala la APK).
  - `Progreso` (SharedPreferences: usos por fecha, planes semanales, checklists del día).
  - `Usos`, `Semana`/`Planificador` (plan semanal sin repetir en el mes), `Recomendador`
    (charlas por actividad o texto libre), `Calendario`.
- `app/src/main/java/cl/sacyr/rutaitata/charlas/ui/`: pantallas Compose. Pestañas Semana,
  Temas, Plan mes e Historial (`Inicio.kt`), ficha (`Detalle.kt`), recomendador
  (`Recomendar.kt`), información (`Acerca.kt`). Navegación por rutas en `AppCharlas.kt`.
- `tools/generar_banco_md.py`: genera `docs/BANCO_CHARLAS.md`. Replica la puntuación de
  `Recomendador.kt` para la tabla de charlas recomendadas; si cambias una, cambia la otra.
- `.github/workflows/validar-charlas.yml`: pruebas en PRs y en `main`, y verifica que
  `docs/BANCO_CHARLAS.md` esté al día.
- `.github/workflows/publicar-app.yml`: publica la APK firmada como release al llegar a
  `main` un `versionName` nuevo.

## Editar el banco de charlas (`charlas.json`)

- Cada charla: `id`, `codigo`, `especialidad`, `titulo`, `porQue`, `checklist` (exactamente 3
  puntos), `normativa`, `reglaOro`, `preguntaCierre`.
- **Nunca reutilizar ni cambiar un `id`**: el historial y los planes de los teléfonos se
  guardan por `id`. Una charla nueva usa el siguiente `id` libre de su especialidad.
- `codigo` es correlativo por especialidad (`OA-01`, `OA-02`…); una prueba lo exige. Las
  charlas se ordenan por especialidad y código.
- Al cambiar el contenido: **subir `version`**, actualizar `fecha` y escribir `novedades`
  (es el aviso que ve el capataz). Sin subir la versión, los teléfonos no lo descargan.
- Títulos únicos. Mínimo 25 charlas por especialidad.
- Cada actividad debe tener **al menos 5 charlas bien enfocadas** (puntaje ≥ 6 en el
  `Recomendador`); lo exige `RecomendadorTest`. Si agregas una actividad, ajusta sus
  `palabras` o escribe charlas hasta cumplirlo.
- Normativa: citar solo lo que se pueda respaldar (Ley 16.744, DS 594, DS 44/2024 que
  reemplazó al DS 40, Ley 18.290, Manual de Carreteras MOP Vol. 6, MST Cap. 5, NCh…). Si no
  hay certeza del artículo, citar la norma sin número. Las "Reglas de Oro" son principios
  genéricos, no la redacción oficial de Sacyr.
- Después de editar: `python3 tools/generar_banco_md.py` y `./gradlew testDebugUnitTest`.

## Publicar una versión de la app

1. Subir `versionCode` (+1) y `versionName` en `app/build.gradle.kts`.
2. Escribir `app/novedades-version.txt` (texto del aviso de actualización).
3. Llevar el cambio a `main`. El workflow compila, verifica la huella del certificado
   (`1038edcb…f9ad3dc`) y crea la release `charlas-v<versión>` **sin marcarla como latest**.

Cuidados:
- El repositorio también publica releases de otra app (Bitácora Vial, tags `vX.Y`). La app de
  charlas solo acepta tags `charlas-v*` con una APK `charlas-ruta-itata-*.apk`; no cambies eso.
- La clave de firma (`charlas-ruta-itata.jks`) **nunca va al repositorio**. Vive en los
  secrets `FIRMA_KEYSTORE_BASE64` y `FIRMA_CLAVE` de GitHub Actions y en poder del
  responsable del proyecto. Si se pierde, los teléfonos no podrán actualizarse encima.
- Un cambio solo de contenido no necesita versión nueva de la app.

## Convenciones

- Rama de trabajo, PR a `main`; el check *Validar banco de charlas* debe pasar.
- Mensajes de commit en español, describiendo el porqué.
- No se usa minificación (R8) en release: un fallo al iniciar rompería también el
  actualizador.
