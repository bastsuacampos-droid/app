# Charlas Ruta Itata

App Android con el **Banco Mensual de Charlas de 5 Minutos** del proyecto
*Mejoramiento de la Ruta Itata*. Sirve como guía para que capataces y
supervisores hagan la charla de inicio de jornada (08:00) con sus cuadrillas.

## Qué incluye

- **24 charlas** organizadas en 4 semanas, de lunes a sábado.
- **5 especialidades**: Maquinaria Pesada, Control de Tránsito / Paleteros,
  Cuadrillas de Asfalto, Obras de Arte / Manuales y Riesgos Transversales / Clima.
  Todas las especialidades aparecen cada semana.
- Cada charla trae: **título**, **el porqué** (mensaje clave), **3 puntos de
  control** marcables en terreno, **respaldo estándar** (Regla de Oro y
  normativa chilena) y una **pregunta de cierre** para la cuadrilla.
- **Charla de hoy** según el día del ciclo; el domingo muestra la del lunes
  para prepararla con tiempo.
- Registro de charlas realizadas y avance del ciclo (se guarda en el teléfono).
- **Compartir** la charla como texto (WhatsApp, correo).
- Botones **A− / A+** para agrandar la letra en terreno.
- **Recibe charlas nuevas** automáticamente al abrirse (ver más abajo) y
  funciona sin conexión con la última versión guardada.

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

1. Editar `app/src/main/assets/charlas.json`.
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
