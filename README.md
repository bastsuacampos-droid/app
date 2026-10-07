# Charlas Ruta Itata

App Android con el **Banco Mensual de Charlas de 5 Minutos** del proyecto
*Mejoramiento de la Ruta Itata*. Sirve como guía para que capataces y
supervisores hagan la charla de inicio de jornada (08:00) con sus cuadrillas.

## Qué incluye

- **308 charlas** en 9 especialidades, con al menos 25 cada una: Maquinaria
  Pesada (MP), Control de Tránsito / Paleteros (CT), Cuadrillas de Asfalto (AS),
  Obras de Arte / Manuales (OA), Riesgos Transversales / Clima (RT),
  Carpintería (CA), Enfierradura (EN), Hormigonado (HO) y Movimiento de
  Tierra (MT). Cada una tiene un código (por ejemplo `CA-07`).
- Cada charla trae: **título**, **el porqué** (mensaje clave), **3 puntos de
  control** marcables en terreno, **respaldo estándar** (Regla de Oro y
  normativa chilena) y una **pregunta de cierre** para la cuadrilla.
- **Semana**: marcas las especialidades que vas a trabajar (en orden) y los
  días con charla, y la app arma el plan de lunes a sábado con charlas que no
  has dictado ni planificado en el mes. Cada día se puede cambiar (otra charla
  de la misma especialidad o de otra, al azar) y el plan se comparte por WhatsApp. Se
  puede planificar hasta 4 semanas hacia adelante.
- **¿Qué actividad harás?**: desde Semana (o el menú ⋮ de un día) eliges una
  actividad típica de la obra o la escribes ("hormigonado del cabezal") y la app
  recomienda charlas acordes, con opción de otra al azar; la elegida queda en el
  plan de ese día junto con la actividad. Hay 40 actividades que cubren las
  tareas de la obra, cada una con al menos 5 charlas bien enfocadas (una prueba
  automática lo verifica). Las actividades y sus palabras clave
  están en `actividades` dentro de `charlas.json` y se actualizan como el resto
  del banco.
- **Temas**: eliges la especialidad en el selector y ves cuáles charlas ya usaste
  este mes y cuáles quedan disponibles. "Siguiente disponible" abre la primera
  que aún no dictas, y se pueden ocultar las ya usadas.
- **Registro mensual**: al terminar, "Registrar charla dictada hoy" la marca
  como usada. Si abres una charla ya usada en el mes, la app avisa y propone
  otra de la misma especialidad. El día 1 de cada mes todas vuelven a quedar
  disponibles.
- **Historial** por mes, con el total por especialidad; un registro hecho por
  error se puede borrar.
- **Plan sugerido** de 4 semanas (lunes a sábado) que cubre todas las
  especialidades, con la charla sugerida para hoy.
- **Compartir** la charla como texto (WhatsApp, correo) y botones **A− / A+**
  para agrandar la letra en terreno.
- **Recibe charlas nuevas** automáticamente al abrirse (ver más abajo) y
  funciona sin conexión con la última versión guardada.

El historial se guarda solo en el teléfono del capataz.

El contenido completo también está en
[`docs/BANCO_CHARLAS.md`](docs/BANCO_CHARLAS.md) para imprimir.

## Actualizaciones de charlas dentro de la app

Cada vez que se abre (o se vuelve a ella, como máximo cada 30 minutos), la app
descarga el banco publicado en la rama `main` de este repositorio:

```
https://raw.githubusercontent.com/bastsuacampos-droid/app/main/app/src/main/assets/charlas.json
```

Si su `version` es mayor que la que tiene el teléfono y pasa la validación, la
app lo guarda, lo usa de inmediato y muestra un aviso con las `novedades`. Sin
señal, sigue funcionando con la última versión guardada. También se puede
buscar a mano en *Ciclo y normativa → Buscar actualizaciones*.

### Publicar una actualización

1. Editar `app/src/main/assets/charlas.json`. Para agregar una charla, sumarla
   a `charlas` con un `id` nuevo (nunca reutilizar uno, porque el historial
   del teléfono se guarda por `id`) y el siguiente `codigo` de su
   especialidad. El plan sugerido está en `plan` (semana, día, id de charla).
2. **Subir `version`** en uno (por ejemplo, de 1 a 2), poner la `fecha` y
   escribir en `novedades` qué cambió. Si no se sube la versión, los teléfonos
   no descargan el cambio.
3. Regenerar el documento para imprimir y validar:

   ```bash
   python3 tools/generar_banco_md.py
   ./gradlew testDebugUnitTest
   ```

4. Llevar el cambio a `main`. El check *Validar banco de charlas* de GitHub
   corre las mismas pruebas; los teléfonos reciben la nueva versión la próxima
   vez que abran la app (GitHub puede tardar unos 5 minutos en servirla).

La URL se define en `app/build.gradle.kts` (`URL_CONTENIDO`) por si el banco se
publica en otro lugar.

## Actualizar la app desde la misma app

Desde la versión 1.1, la app revisa al abrirse si hay una versión nueva publicada
en las releases de este repositorio. Si la hay, muestra el aviso **Nueva versión
de la app** con el botón para descargarla e instalarla; las charlas, planes e
historial se mantienen. La primera vez, Android pide autorizar a la app para
instalar actualizaciones ("Permitir de esta fuente").

El repositorio también publica releases de otras apps, por eso las de esta app
usan el tag `charlas-v<versión>`, se publican sin marcarse como *latest* y la app
ignora cualquier otra release.

### Publicar una versión nueva

1. En `app/build.gradle.kts`, subir `versionCode` (en uno) y `versionName`
   (por ejemplo, de `1.1` a `1.2`).
2. Escribir en `app/novedades-version.txt` qué trae la versión (es el texto que
   ve el capataz en el aviso).
3. Llevar el cambio a `main`. El workflow *Publicar versión de la app* compila la
   APK, verifica que esté firmada con la clave de la app y crea la release
   `charlas-v1.2`. Los teléfonos la ofrecen la próxima vez que se abra la app.

### Firma (una sola vez)

Android solo instala una actualización si viene firmada con la misma clave que
la app instalada. La clave (`charlas-ruta-itata.jks`) **no está en el
repositorio**: se guarda en dos secrets de GitHub Actions
(*Settings → Secrets and variables → Actions*):

- `FIRMA_KEYSTORE_BASE64`: el archivo `.jks` codificado en base64.
- `FIRMA_CLAVE`: la contraseña del archivo.

Sin esos secrets el workflow no publica (deja un aviso). Si la clave se pierde,
no se pueden publicar más actualizaciones para los teléfonos que ya tienen la
app: guárdala también fuera de GitHub. El workflow verifica que la huella
SHA-256 del certificado sea `1038edcb…f9ad3dc` antes de publicar.

Para compilar una versión firmada a mano:

```bash
FIRMA_KEYSTORE=/ruta/charlas-ruta-itata.jks FIRMA_CLAVE='…' ./gradlew assembleRelease
```

## Compilar

Requiere JDK 17+ y el Android SDK (API 35). Abrir el proyecto en Android
Studio, o desde la terminal:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Android 8.0 (API 26) o superior.

## Aviso

La app es una guía de apoyo. Las Reglas de Oro se presentan como principios;
su redacción oficial y los valores propios del proyecto (distancias,
velocidades, relevos) deben validarse con Prevención de Riesgos y el sistema
de gestión SSOMA vigente. No reemplaza los procedimientos de trabajo seguro,
el plano de desvío aprobado ni el registro oficial de asistencia.
