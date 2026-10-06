# Charlas 5 Min – Mejoramiento Ruta Itata

App Android con el **Banco Mensual de Charlas de 5 Minutos** para capataces y supervisores del proyecto
"Mejoramiento de la Ruta Itata": 24 charlas operativas de inicio de jornada (4 semanas, lunes a sábado),
organizadas por especialidad.

| Día | Especialidad |
|---|---|
| Lunes | Maquinaria Pesada |
| Martes | Control de Tránsito / Paleteros |
| Miércoles | Cuadrillas de Asfalto |
| Jueves | Obras de Arte / Manuales |
| Viernes | Riesgos Transversales / Clima |
| Sábado | Refuerzo de una especialidad crítica |

Cada charla trae: **Título**, **El por qué** (mensaje clave), **3 puntos de control** verificables en terreno,
**Respaldo estándar** (Regla de Oro + normativa chilena) y una **pregunta de cierre**.

La copia imprimible de todo el contenido está en [`docs/banco_charlas.md`](docs/banco_charlas.md).

## Funciones

- **Hoy**: charla que corresponde al día según la semana del ciclo (ajustable S1–S4), avance del mes y charla modelo.
- **Plan mensual**: las 4 semanas con fechas; marca las charlas ya dictadas.
- **Especialidades**: filtro por foco de faena.
- **Marco**: rol, marco normativo (Ley 16.744, DS 594, DS 44/2024 ex DS 40, Ley 18.290, Manual de Carreteras MOP) y guía para dictar en 5 minutos.
- En cada charla: checklist interactivo, **texto grande** (A+) para leer en terreno, **compartir** por WhatsApp/correo y registro "dictada hoy".
- Funciona sin internet.

## Instalar

Descarga el APK desde la pestaña **Actions** del repositorio (artefacto `charlas-ruta-itata-apk` del último build),
cópialo al teléfono y ábrelo (Android 8.0 o superior; hay que permitir "instalar apps de origen desconocido").

## Compilar

Requiere JDK 17+ y Android SDK (plataforma 35).

```bash
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # valida estructura del banco y cálculo del ciclo
```

El contenido de las charlas está en `app/src/main/java/cl/rutaitata/charlas/data/BancoCharlas.kt`.

> Las Reglas de Oro se citan en forma resumida. Antes de usar el banco, valídalo con la versión vigente de los
> estándares, procedimientos y matriz de riesgos del sistema SSOMA del proyecto.
