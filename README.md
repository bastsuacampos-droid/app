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
- Funciona sin conexión a internet.

El contenido completo también está en
[`docs/BANCO_CHARLAS.md`](docs/BANCO_CHARLAS.md) para imprimir.

## Editar las charlas

El contenido vive en `app/src/main/assets/charlas.json`. Después de editarlo:

```bash
python3 tools/generar_banco_md.py   # regenera docs/BANCO_CHARLAS.md
./gradlew testDebugUnitTest         # valida la estructura del banco
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
