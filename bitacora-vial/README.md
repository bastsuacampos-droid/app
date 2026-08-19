# Bitácora Vial

App personal para que un capataz registre partes diarios de una obra vial:
cubicación de tareas, asistencia de personal (con horas extra y motivo),
fotos con anotaciones, documentos del proyecto, historial/respaldo y
configuración. Construida a partir del bosquejo interactivo (`../mockup`)
acordado con el usuario.

## Por qué este stack

Esta sesión no tenía Flutter ni el Android SDK instalados, así que no era
posible compilar un `.apk` nativo directamente aquí. Se optó (decisión del
usuario) por **React + TypeScript + Vite, como PWA instalable**:

- Se compila y se prueba en cualquier entorno con Node (`npm run build`).
- Queda instalable en Android desde el navegador ("Agregar a pantalla de
  inicio"), funciona offline y usa cámara/notificaciones vía APIs web.
- El día que se necesite un `.apk` real para la Play Store, este mismo
  código se puede envolver con **Capacitor** (`npx cap add android`) sin
  reescribir nada — solo hace falta tener el Android SDK disponible.

## Cómo correrla

```bash
npm install
npm run dev       # servidor de desarrollo
npm run build     # compila a dist/ (tsc + vite build)
npm run preview   # sirve dist/ para probarlo como en producción
```

## Cómo verificar cambios

Hay dos scripts de Playwright en `scripts/` que abren la app en un Chromium
real y navegan las pantallas principales, revisando la consola en busca de
errores:

```bash
npm run dev &                        # o vite preview tras un build
node scripts/smoke-test.mjs          # recorre las 12 pantallas
node scripts/interaction-test.mjs    # cubicación, asistencia, foto+dibujo,
                                      # y confirma que Horas Extra refleja
                                      # los datos recién ingresados
```

Úsalos después de cualquier cambio grande — así fue como se detectaron y
corrigieron, antes de entregar la app, dos bugs reales: una condición de
carrera al crear el parte del día (dos pantallas intentaban crearlo a la
vez) y una violación de las reglas de `useLiveQuery` de Dexie (no se puede
escribir en la base de datos dentro de una consulta reactiva).

## Estructura del proyecto

```
src/
  types/models.ts       Todas las interfaces de dominio (Parte, Trabajador,
                         Partida, Foto, Documento, Settings, etc.)
  lib/
    db.ts                Esquema de IndexedDB (Dexie) + semilla de datos
    queries.ts           Agregaciones: avance acumulado, horas extra
                          mensuales, resumen de asistencia, etc.
    useTodayParte.ts      Hook reactivo para "el parte de hoy"
    useSettings.ts        Hook reactivo para configuración
    date.ts / export.ts   Formateo de fechas y exportación (CSV/PDF/backup)
  components/            UI compartida: Header, BottomNav, Toggle,
                          StatusBadge, Icon.tsx (todos los íconos SVG),
                          ErrorBoundary
  styles/
    tokens.css            Paleta de colores y tipografía (igual al bosquejo)
    global.css             Clases reutilizables: .card, .btn-*, .chip, etc.
  features/<pantalla>/    Un componente de página por pantalla, agrupado
                          por función (dashboard, partes, cubicacion,
                          asistencia, fotos, horasExtra, documentos,
                          historial, mas, configuracion, onboarding)
```

### Cómo agregar una pantalla o función nueva

1. Si necesitas datos nuevos, agrégalos a `types/models.ts` y a la tabla
   correspondiente en `lib/db.ts` (súbele la versión del `stores()` si
   cambias índices existentes — Dexie migra solo).
2. Escribe las consultas/agregaciones puras en `lib/queries.ts`.
3. Crea la carpeta en `features/` y usa los componentes de `components/`
   y las clases de `styles/global.css` para que se vea consistente.
4. Si la pantalla necesita **crear** algo como efecto de montarse (como
   "el parte de hoy" o "la asistencia por defecto"), hazlo en un
   `useEffect`, nunca dentro de la función que le pasas a `useLiveQuery`
   — Dexie lo rechaza porque esa consulta corre en una transacción de
   solo lectura (por eso existe el patrón `ensureXExists()` +
   `useLiveQuery` de solo lectura en `useTodayParte.ts`).
5. Agrega la ruta en `src/App.tsx` y, si corresponde, un ítem en
   `components/BottomNav.tsx`.

## Qué es real y qué es respaldo local (no hay backend)

Todo se guarda **localmente en el dispositivo** (IndexedDB vía Dexie):
partes, cubicación, asistencia, fotos y documentos (como archivos
binarios). No hay servidor ni cuenta de usuario — coherente con que es un
registro personal del capataz.

- **Respaldo**: "Respaldar ahora" (Historial) descarga un `.json` con toda
  la base de datos (fotos y documentos incluidos, en base64). Es un
  respaldo real y restaurable, pero manual: hay que guardarlo tú mismo
  (por ejemplo, subiéndolo a Google Drive). Automatizar la subida a la
  nube requeriría integrar una API externa (Google Drive, por ejemplo) y
  quedó fuera de este alcance.
- **Exportaciones**: cubicación a CSV, y horas extra a CSV/PDF (con
  `jsPDF`) son generación real de archivos, no maquetas.
- **Permisos y notificaciones**: la pantalla de bienvenida pide cámara y
  notificaciones de verdad (con un timeout de seguridad para que nunca se
  quede pegada si el navegador no responde). El recordatorio diario tiene
  su toggle en Configuración, pero **la programación real del aviso
  (Notification a las 18:00) no está implementada todavía** — es el
  siguiente paso natural, con un Service Worker + `periodicSync` o, más
  simple, revisar la hora cada vez que se abre la app.

## Próximos pasos sugeridos

- [ ] Programar el recordatorio diario real (notificación local).
- [ ] Subida automática del respaldo a una nube (Google Drive API).
- [ ] Empaquetar como `.apk` con Capacitor cuando haya Android SDK
      disponible, y ahí sí pedir permisos nativos (cámara, ubicación,
      notificaciones) con los diálogos del sistema operativo.
- [ ] Code-splitting de `jspdf` (hoy se incluye en el bundle principal;
      el build avisa que ese chunk pesa ~730 KB) con `import()` dinámico
      al exportar.
- [ ] Editar/eliminar partidas y trabajadores ya creados (hoy solo se
      pueden agregar).
- [ ] Probar en un dispositivo Android real (cámara, `capture=environment`,
      instalación como PWA) — esta sesión solo pudo verificarlo en
      Chromium de escritorio sin cámara física.
- [ ] `npm audit` marca 7 vulnerabilidades (react-router-dom, vite/esbuild,
      dompurify vía jspdf) cuyo arreglo automático implica saltos de
      versión mayor. Ninguna aplica a cómo se usan hoy estas librerías
      aquí (no se procesa HTML de terceros, no hay servidor de desarrollo
      expuesto), pero conviene revisar y actualizar en el próximo ciclo
      de trabajo en vez de dejarlas indefinidamente.
