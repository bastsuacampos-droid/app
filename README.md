# Charlas Ruta Itata

App Android con el **Banco Mensual de Charlas de 5 Minutos** del proyecto
*Mejoramiento de la Ruta Itata*. Sirve como guía para que capataces y
supervisores hagan la charla de inicio de jornada (08:00) con sus cuadrillas.

## Qué incluye

- **125 charlas**, 25 por cada especialidad: Maquinaria Pesada (MP),
  Control de Tránsito / Paleteros (CT), Cuadrillas de Asfalto (AS),
  Obras de Arte / Manuales (OA) y Riesgos Transversales / Clima (RT).
  Cada una tiene un código (por ejemplo `MP-07`).
- Cada charla trae: **título**, **el porqué** (mensaje clave), **3 puntos de
  control** marcables en terreno, **respaldo estándar** (Regla de Oro y
  normativa chilena) y una **pregunta de cierre** para la cuadrilla.
- **Semana**: marcas las especialidades que vas a trabajar (en orden) y los
  días con charla, y la app arma el plan de lunes a sábado con charlas que no
  has dictado ni planificado en el mes. Cada día se puede cambiar (otra charla
  de la misma especialidad o de otra) y el plan se comparte por WhatsApp. Se
  puede planificar hasta 4 semanas hacia adelante.
- **Temas**: eliges la especialidad y ves cuáles charlas ya usaste
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
