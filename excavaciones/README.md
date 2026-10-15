# Excavaciones Ruta Itata

App Android (Kotlin) para registrar en terreno, sin señal, las excavaciones de cada jornada
y emitir el **Reporte Diario de Control de Excavaciones** en PDF. Es un módulo aparte del
repositorio (`:excavaciones`), con su propio `applicationId`; no comparte datos con la app de
charlas.

> Estado: base de datos, repositorio, agrupación y generador de PDF listos y probados.
> Pendiente: pantallas (dashboard, formulario, reporte), captura GPS y fotos.

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
    ├── data/
    │   ├── RepositorioExcavaciones.kt   única puerta a los datos (Flow + suspend)
    │   └── local/
    │       ├── BaseDatos.kt         RoomDatabase, semilla de tipos de terreno, migraciones
    │       ├── Conversores.kt       LocalDate ⇄ días, Lado/Sentido ⇄ código
    │       ├── entidades/Entidades.kt   @Entity: sectores, frentes, excavaciones, fotos, tipos_terreno
    │       └── dao/Daos.kt          consultas (resumen del día, rango para reporte, correlativos)
    ├── reporte/
    │   ├── DatosReporte.kt          entrada del reporte y nombre del archivo
    │   ├── Plano.kt                 primitivas de dibujo (texto, línea, rectángulo) y paleta
    │   ├── MaquetadorReporte.kt     maquetación pura: encabezados, tablas, saltos, totales, firmas
    │   ├── GeneradorReportePdf.kt   pinta las páginas con android.graphics.pdf.PdfDocument
    │   └── CompartirReporte.kt      FileProvider: compartir (ACTION_SEND), ver, guardar copia
    └── ui/                          (pendiente) Compose: Dashboard, Formulario, Reporte
```

## Modelo de datos

```
sectores (id, nombre UNIQUE NOCASE)
   └─< frentes (id, sectorId, lado 'N'|'S', sentido 'CH-CO'|'CO-CH')   UNIQUE(sectorId, lado, sentido)
          └─< excavaciones (id, fecha, frenteId, numero, progresiva, largo, ancho, profundidad,
          │                 tipoTerrenoId, latitud, longitud, precisionGps, observaciones, …)
          │                 UNIQUE(frenteId, numero)
          └── fotos (excavacionId, orden 0-2, archivo)   ON DELETE CASCADE
tipos_terreno (id, nombre UNIQUE NOCASE, orden, activo)
```

- El **frente** (sector + lado + sentido) da la numeración correlativa: el número es único
  dentro del frente y sigue de un día a otro (la excavación 12 de un frente es siempre la
  misma). Se crea solo al registrar la primera excavación.
- El **volumen** no se guarda: es largo × ancho × profundidad, se suma sin redondear y se
  redondea a 2 decimales al mostrar.
- Lado y sentido se guardan por su `codigo`: no cambiar los códigos (sí las etiquetas).
- Sectores, frentes y tipos de terreno en uso no se pueden borrar; los tipos se desactivan.

### Cambiar el esquema

Subir `version` en `BaseDatos`, agregar la `Migration` a `MIGRACIONES` y compilar: Room
escribe el JSON de la nueva versión en `schemas/`. Versionar ese JSON y probar la migración.
Nunca usar `fallbackToDestructiveMigration`: borraría los registros de obra.

## Reporte PDF

A4 horizontal con encabezado (título, obra, fecha o período, sector, responsable, emisión),
una banda por sector, subtítulo por lado y una tabla por sentido (N°, progresiva/GPS,
dimensiones L × A × P, volumen, tipo de terreno, observaciones; más la fecha si el reporte
abarca varios días) con subtotal. Las tablas largas siguen en la página siguiente repitiendo
títulos; al final van los totales y los cuadros de firma del Jefe de Terreno y del ITO, y
cada página lleva "Página X de N".

`MaquetadorReporte` no depende de Android: las pruebas revisan saltos de página, márgenes y
contenido en la JVM. `GeneradorReportePdf` solo mide el texto y pinta.

## Comandos

```bash
./gradlew :excavaciones:testDebugUnitTest   # pruebas (dominio, maquetación, repositorio con Robolectric)
./gradlew :excavaciones:assembleDebug       # excavaciones/build/outputs/apk/debug/excavaciones-debug.apk
```
