# Excavaciones Ruta Itata

App Android (Kotlin) para registrar en terreno, sin señal, las excavaciones de cada jornada
y emitir el **Reporte Diario de Control de Excavaciones** en PDF. Es un módulo aparte del
repositorio (`:excavaciones`), con su propio `applicationId`; no comparte datos con la app de
charlas.

## Uso en terreno

1. **Dashboard**: elige el día (flechas o calendario); muestra cantidad y volumen del día y las
   excavaciones agrupadas por Sector → Lado → Sentido, en orden correlativo. Las que no tienen
   foto se marcan "Sin foto".
2. **Nueva excavación** (botón naranja): abre con la fecha del dashboard y el último sector,
   lado, sentido y tipo de terreno usados. Arriba está el **tipo de excavación**: plantillas
   (pocillo de inspección, zanja de 0,60 m…) que llenan medidas, terreno y observaciones. Se
   elige una vez y las siguientes excavaciones nuevas ya vienen con el último tipo usado. Una
   medida que la plantilla deja vacía (el largo de una zanja) se escribe en cada una. "Guardar
   medidas actuales como tipo" crea una plantilla con lo que ya está escrito. El número es automático (el siguiente del frente) o
   manual. El volumen se calcula mientras se escriben las medidas (acepta coma o punto).
   "Obtener ubicación" toma una lectura GPS; "Cámara" o "Galería" agregan hasta 3 fotos. Al
   tocar una foto se abre a pantalla completa y se amplía con dos dedos o doble toque.
   "Guardar y otra" deja el formulario listo para la siguiente del mismo frente. Guardar sin
   foto pide confirmación: la foto es el respaldo de cómo quedó la excavación.
3. **Reporte PDF**: período (hoy, ayer, esta semana o fechas), sector o todos, responsable y
   anexo fotográfico (se puede desactivar para un PDF liviano). Luego **Visualizar** (visor
   dentro de la app; tocar una página la amplía), **Compartir** (WhatsApp, correo…) o
   **Guardar copia** donde se elija.
4. **Tipos de excavación** y **Tipos de terreno** (menú ⋮): crear, editar o borrar.
   **Sectores** (menú ⋮): corregir el nombre de un sector; si se cambia al de otro existente, se
   ofrece unirlos (las excavaciones pasan con su lado y sentido; si un número ya está usado, la
   que se mueve toma el siguiente libre y se avisa). Un sector vacío se puede borrar. Al
   escribir un sector nuevo casi igual a uno existente ("Sectro A"), el formulario ofrece el
   existente para no crear un duplicado (`dominio/Sectores.kt`).
5. **Respaldo de datos** (menú ⋮): guarda un .zip con todos los registros y fotos donde se elija
   (Drive, Descargas) y lo restaura en otro teléfono. El dashboard lo recuerda si pasan 7 días
   sin respaldo. En la pantalla de reporte, **Exportar planilla para Excel** genera un CSV del
   período y sector elegidos para la oficina técnica.

Todo funciona sin señal; los datos y fotos quedan solo en el teléfono, por eso el respaldo.

### Cierre de días e integridad del registro

- **Cerrar días y emitir reporte definitivo** (pantalla de reporte): cierra los días del período
  (todos los sectores). En un día cerrado no se puede agregar, editar, mover ni borrar
  excavaciones; el repositorio lo impide, no solo la interfaz. El PDF dice **CERRADO** (cuándo y
  quién) o **BORRADOR** (qué días siguen abiertos).
- **Reabrir** (dashboard) exige un motivo; queda en `reaperturas` y sale en los reportes del día.
- Cada foto guarda su **origen** (cámara o galería) y lleva **estampados** fecha y hora y, si ya
  se habían obtenido, las coordenadas. El anexo dice "cámara" o "galería" en cada leyenda.

### Respaldo

- `Respaldo.kt`: ZIP con `respaldo.json` (todas las tablas, formato lógico versionado con
  `FORMATO`) y `fotos/`. Restaurar reemplaza todo y no toca nada si el archivo está dañado;
  ignora rutas peligrosas dentro del ZIP. Un campo nuevo en una tabla debe agregarse al JSON
  (leerlo como opcional) y a `RespaldoTest`.
- Respaldo automático de Android (`res/xml/respaldo_android.xml`, `extraccion_datos.xml`): solo
  base y preferencias, porque con fotos se pasaría del tope de 25 MB de Google y dejaría de
  respaldar todo. En el traspaso directo entre teléfonos sí van las fotos.

## Estructura

```
excavaciones/
├── build.gradle.kts                 Room (KSP), esquema exportado a schemas/
├── schemas/…/BaseDatos/1.json       esquema de cada versión de la base (para migraciones)
└── src/main/java/cl/sacyr/rutaitata/excavaciones/
    ├── AppExcavaciones.kt           Application: contenedor de dependencias (sin framework DI)
    ├── dominio/                     Kotlin puro, sin Android
    │   ├── Calzada.kt               enum Lado (Norte/Sur) y Sentido (Chillán⇄Concepción)
    │   ├── ExcavacionDetalle.kt     modelo de lectura + cálculo de volumen
    │   ├── Agrupador.kt             Sector → Lado → Sentido → Excavación, orden natural
    │   ├── BorradorExcavacion.kt    datos del formulario + ValidadorExcavacion
    │   └── Formato.kt               números, fechas y coordenadas en formato chileno
    ├── MainActivity.kt
    ├── data/
    │   ├── RepositorioExcavaciones.kt   única puerta a los datos (Flow + suspend)
    │   ├── ArchivosFotos.kt         importa fotos (gira según EXIF, reduce a 2560 px, JPEG), la versión para el PDF y limpia huérfanas
    │   ├── ProveedorUbicacion.kt    una lectura GPS a pedido (sin Google Play Services)
    │   ├── Preferencias.kt          responsable, último frente y último tipo de excavación usados
    │   └── local/
    │       ├── BaseDatos.kt         RoomDatabase, semilla de tipos de terreno, migraciones
    │       ├── Conversores.kt       LocalDate ⇄ días, Lado/Sentido ⇄ código
    │       ├── entidades/Entidades.kt   @Entity: sectores, frentes, excavaciones, fotos, tipos_terreno
    │       └── dao/Daos.kt          consultas (resumen del día, rango para reporte, correlativos)
    ├── reporte/
    │   ├── DatosReporte.kt          entrada del reporte y nombre del archivo
    │   ├── Plano.kt                 primitivas de dibujo (texto, línea, rectángulo) y paleta
    │   ├── MaquetadorReporte.kt     maquetación pura: encabezados, tablas, saltos, totales, firmas
    │   ├── GeneradorReportePdf.kt   escribe el PDF: fotos JPEG tal cual, texto con Roboto incrustada
    │   ├── EscritorPdf.kt           objetos, flujos y tabla xref de un PDF 1.4
    │   ├── FuenteTrueType.kt        métricas de la fuente y subconjunto con los glifos usados
    │   └── CompartirReporte.kt      FileProvider: compartir (ACTION_SEND), ver, guardar copia
    └── ui/                          Compose + ViewModel (MVVM), navegación con navigation-compose
        ├── Inicio.kt                dashboard del día
        ├── Formulario.kt / FormularioViewModel.kt   registro y edición
        ├── Reporte.kt / ReporteViewModel.kt          filtros, generación y visor PDF (PdfRenderer)
        ├── TiposExcavacion.kt       plantillas de excavación (lista y diálogo)
        ├── TiposTerreno.kt          catálogo del desplegable
        └── Componentes.kt, Tema.kt, Navegacion.kt
```

## Modelo de datos

```
sectores (id, nombre UNIQUE NOCASE)
   └─< frentes (id, sectorId, lado 'N'|'S', sentido 'CH-CO'|'CO-CH')   UNIQUE(sectorId, lado, sentido)
          └─< excavaciones (id, fecha, frenteId, numero, progresiva, largo, ancho, profundidad,
          │                 tipoTerrenoId, latitud, longitud, precisionGps, observaciones, …)
          │                 UNIQUE(frenteId, numero)
          └── fotos (excavacionId, orden 0-2, archivo, tomadaEn)   ON DELETE CASCADE
tipos_terreno (id, nombre UNIQUE NOCASE, orden, activo)
tipos_excavacion (id, nombre UNIQUE NOCASE, largo?, ancho?, profundidad?, tipoTerrenoId?, observaciones?)
   plantillas del formulario; no se enlazan con las excavaciones (borrar una no cambia nada registrado)
```

- El **frente** (sector + lado + sentido) da la numeración correlativa: el número es único
  dentro del frente y sigue de un día a otro (la excavación 12 de un frente es siempre la
  misma). Se crea solo al registrar la primera excavación.
- El **volumen** no se guarda: es largo × ancho × profundidad, se suma sin redondear y se
  redondea a 2 decimales al mostrar.
- Lado y sentido se guardan por su `codigo`: no cambiar los códigos (sí las etiquetas).
- Sectores, frentes y tipos de terreno en uso no se pueden borrar; los tipos se desactivan.

### Fotos y permisos

- Las fotos de cámara se toman con la app de cámara del teléfono (`TakePicture`) y las de
  galería con el selector del sistema: no se piden permisos de cámara ni de almacenamiento.
  No declarar `CAMERA` en el manifiesto: obligaría a pedirlo para usar la cámara del sistema.
- Ubicación: `ACCESS_FINE_LOCATION`/`COARSE`, solo al tocar "Obtener ubicación".
- Si Android cierra la app mientras la cámara está abierta, el formulario se recupera
  (`SavedStateHandle`) y la foto llega igual.
- Resolución: la foto se guarda con lado mayor de 2560 px (≈ 5 MP, JPEG 90, ~1-2 MB), y en el
  PDF va a 1600 px (JPEG 82, ~300-500 KB). Las tomadas antes de este cambio quedaron en 1600 px.

### Cambiar el esquema

Subir `version` en `BaseDatos`, agregar la `Migration` a `MIGRACIONES` y compilar: Room
escribe el JSON de la nueva versión en `schemas/`. Versionar ese JSON y agregar su caso a
`MigracionesTest` (crea la base de la versión anterior desde su JSON y la abre con Room).
Versión actual: 4 (v2 agregó `fotos.tomadaEn`; v3, la tabla `tipos_excavacion`; v4, `fotos.origen`,
`cierres_dia` y `reaperturas`).
Nunca usar `fallbackToDestructiveMigration`: borraría los registros de obra.

## Reporte PDF

A4 horizontal con encabezado (título, obra, fecha o período, sector, responsable, emisión),
una banda por sector, subtítulo por lado y una tabla por sentido (N°, progresiva/GPS,
dimensiones L × A × P, volumen, tipo de terreno, observaciones; más la fecha si el reporte
abarca varios días) con subtotal. Las tablas largas siguen en la página siguiente repitiendo
títulos; al final van los totales y los cuadros de firma del Jefe de Terreno y del ITO, y
cada página lleva "Página X de N".

**Anexo fotográfico**: en páginas aparte, en el mismo orden de las tablas, dos excavaciones por
página con hasta tres fotos cada una (sin deformar) y la fecha y hora en que se tomó cada foto.
En la tabla, cada excavación dice "[2 fotos en anexo]" o "[Sin foto]" en rojo. Cada foto va
como JPEG de 1600 px (`ArchivosFotos.LADO_PDF`): al ampliarla en el visor se ven los detalles.

El PDF se escribe sin `PdfDocument`: este guarda las fotos como píxeles sin compresión JPEG y,
para no pasar de decenas de MB, había que dejarlas en ~350 px. `GeneradorReportePdf` incrusta el
JPEG tal cual e incrusta Roboto (`assets/fuentes`, licencia Apache 2.0) solo con los glifos
usados; mide el texto con esa misma fuente. "→" no está en Roboto: se dibuja con trazos.

Todo el reporte es Kotlin puro: `MaquetadorReporteTest` revisa saltos de página, márgenes y
contenido, y `GeneradorReportePdfTest` genera PDFs reales y revisa su estructura. Para mirar uno:
`PDF_PRUEBA_DIR=/ruta ./gradlew :excavaciones:testDebugUnitTest --tests '*GeneradorReportePdfTest*'`.

## Publicar una versión (y actualizar los teléfonos)

1. Subir `versionCode` (+1) y `versionName` en `excavaciones/build.gradle.kts`.
2. Escribir `excavaciones/novedades-version.txt` (el aviso que verá el usuario).
3. Llevar el cambio a `main`. `.github/workflows/publicar-excavaciones.yml` corre las pruebas,
   compila la APK firmada, verifica la huella del certificado y crea la release
   `excavaciones-v<versión>` con `excavaciones-ruta-itata-<versión>.apk`, sin marcarla latest.
4. Los teléfonos con la versión publicada ven el aviso "NUEVA VERSIÓN DE LA APP" al abrirla y
   la instalan encima, sin perder registros ni fotos (también: menú ⋮ → Versión de la app).

La firma usa **la misma clave y los mismos secrets que la app de charlas**
(`FIRMA_KEYSTORE_BASE64`, `FIRMA_CLAVE`; huella `1038edcb…f9ad3dc`). Sin esos secrets el
workflow no publica (deja un aviso). Una APK de prueba (debug) tiene otra firma: no se
actualiza sola ni se puede actualizar con la publicada sin desinstalar, y desinstalar borra los
datos del teléfono. En terreno, usar siempre la publicada.

## Comandos

```bash
./gradlew :excavaciones:testDebugUnitTest   # dominio, maquetación, base y migraciones, fotos y flujo de pantallas (Robolectric)
./gradlew :excavaciones:assembleDebug       # excavaciones/build/outputs/apk/debug/excavaciones-debug.apk
```
