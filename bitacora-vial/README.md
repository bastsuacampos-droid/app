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

## Probarla sin instalar nada

`npm run build:preview` genera `dist-preview/index.html`, un único archivo
autocontenido (usa `vite-plugin-singlefile`, sin el plugin de PWA/service
worker) pensado para publicarse como Claude Artifact y así poder abrirla y
recorrerla desde el navegador sin depender del entorno de desarrollo. Los
datos quedan guardados en el almacenamiento local de esa página — no se
comparten con la app "de verdad" servida desde `npm run dev`/`build`.

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

## Conceptos clave del modelo de datos

- **Préstamo de personal entre frentes**: un `Trabajador` tiene un frente
  "de origen" (`frenteId`), pero su `RegistroAsistencia` de un día
  concreto guarda el frente donde **realmente trabajó** ese día. Si
  difieren, se muestra el badge "Prestado de...". Prestar a alguien
  (`moveTrabajadorAFrente`) simplemente mueve ese registro de un frente a
  otro; nunca hay dos registros del mismo trabajador el mismo día.
- **Estado de una tarea** (`estadoTarea()` en `lib/queries.ts`): nunca se
  guarda en la base — se calcula siempre a partir de lo acumulado vs. lo
  contratado y si hoy ya se cargó algo: *Nueva* (sin tocar) → *Pendiente*
  (algún día anterior avanzó, hoy no) → *En progreso hoy* → *Terminada*.
  Cubicación ordena las partidas por ese estado para que lo pendiente
  aparezca primero.
- **Catálogo sugerido** (`lib/catalogoPartidas.ts`): partidas típicas de
  obra vial agrupadas por categoría para no escribir todo a mano al crear
  una tarea nueva. Es un punto de partida general, no una transcripción
  oficial del Manual de Carreteras — conviene ajustarlo a los ítems reales
  de cada contrato.
- **Fotos por etapa** (`lib/etapas.ts`): cada `Foto` lleva una `etapa`
  (antes/durante/después/inconveniente) y, opcionalmente, la `partidaId`
  de la tarea que documenta. Se eligen ambas justo antes de tomar la
  foto, desde el panel que abre el botón + en Fotos.
- **Cuantificación anidada al agregar una tarea**: el formulario "Agregar
  tarea" de Cubicación pide de una vez el título, la unidad, la cantidad
  contratada (puede dejarse en blanco si aún no se sabe — queda marcada
  "Sin cubicar aún" y se define después tocando "Cubicar esta tarea") y el
  avance de hoy; todo en un solo paso, y el % de avance se recalcula al
  tiro. `NuevoPartePage` además anida un resumen (título + avance + %) de
  las tareas tocadas hoy, sin tener que entrar a Cubicación para verlo.
- **Diseño visual (tokens en `styles/tokens.css`)**: paleta clara con
  acento azul (`--accent`), tomada como base de una app de referencia de
  "reportes diarios" de terreno. Todo color vive en variables CSS —
  nunca hardcodees un hex en un componente; usa `var(--token)` (o agrega
  uno nuevo a `tokens.css` si hace falta) para que el modo oscuro
  (`[data-theme='oscuro']`) siga funcionando en toda la app. La única
  pantalla intencionalmente oscura de punta a punta es el onboarding.
- **`NuevoPartePage` en 5 secciones numeradas**: siguiendo esa misma
  referencia, el parte diario es una sola página con scroll, dividida en
  bloques con el badge circular `.section-number` (1 Ubicación y Fecha,
  2 Asistencia del día con contador y botón directo, 3 Tareas y Avances
  con barra de progreso por tarea, 4 Registro Fotográfico con carrete
  horizontal `.photo-strip` + tomar foto inline, 5 Observaciones). Las
  pantallas dedicadas (Cubicación, Asistencia, Fotos) siguen existiendo
  para el detalle completo; esta vista es el resumen operativo del día.
- **Sub-tareas cubicadas por separado** (`Partida.partidaPadreId`, v3 del
  schema): una partida puede tener sub-partidas propias (p. ej. "Estribo
  N1" → Excavación, Enfierradura, Hormigón H-30), cada una con su propia
  unidad, cantidad contratada y avance diario — se agregan con "+ Agregar
  sub-tarea" dentro de la tarjeta de la tarea padre. En cuanto una tarea
  tiene sub-tareas, deja de cubicarse directamente (su UI de "Ejecutado
  hoy" se oculta y la reemplaza la lista de sub-tareas); si ya tenía
  cantidad acumulada de antes de subdividirla, esa cifra se conserva como
  nota de solo lectura para no perder el dato, pero deja de sumar al
  avance del frente — desde ahí en adelante avanzan las sub-tareas. La
  agrupación es puramente de presentación: `tareasDelDiaAgrupadas()` en
  `lib/queries.ts` arma los grupos leyendo el padre de cada partida
  tocada hoy, así que tanto Cubicación como el resumen anidado de
  `NuevoPartePage` muestran la misma jerarquía sin datos duplicados.
- **Calculadora de cubicación por elementos + memoria de cálculo**
  (`CubicacionPage.tsx`, tabla `medicionesCubicacion` v4 del schema): la
  antigua calculadora de largo×ancho×alto se amplió a varios tipos de
  elemento según la unidad de la partida — prisma rectangular, sección
  trapezoidal y cilíndrico para m³; rectangular y muro con descuento de
  vanos para m²; enfierradura por diámetro para kg (peso ≈ d²/162, d en
  mm — la fórmula estándar de densidad del acero, no específica de
  ninguna norma). **Importante**: es un cálculo geométrico general de uso
  práctico en obra, no una transcripción verificada de NCh 353 Of.2000
  ("Mediciones y cubicaciones en construcción") — antes de usarlo para un
  estado de pago conviene confirmar el criterio de medición exacto de
  cada partida en el contrato; el mismo aviso aparece en la calculadora.
  Disponible tanto para "Cantidad contratada" (al crear la tarea o al
  editarla después con "Cubicar esta tarea") como para "Ejecutado hoy".
  Cada elemento agregado queda guardado como una `MedicionCubicacion`
  (tipo, descripción opcional, dimensiones y subtotal) — visible después
  al abrir "Ver mediciones" en la tarjeta de la tarea, para poder
  revisar de dónde salió cada cifra.
- **Selector de tareas + pestaña Completadas** (`Parte.tareasSeleccionadasIds`,
  `tareasActivasAgrupadas()` / `tareasDisponiblesParaFrentes()` /
  `tareasCompletadas()` en `lib/queries.ts`): la sección "Tareas y
  Avances" de Nuevo Parte ya no se llena solo automáticamente con lo que
  tenga una entrada de cubicación hoy — el botón "Seleccionar tarea" abre
  un desplegable (mismo patrón visual que "Seleccionar punto de trabajo")
  con las tareas del frente elegido, recomendando primero las
  *pendientes* (avanzaron un día anterior pero no hoy) y las *en
  progreso hoy*, con badge "Recomendada" / "En progreso"; las
  *terminadas* no aparecen ahí. Cualquier tarea que ya tenga cubicación
  hoy se suma sola a la selección (para no perder de vista algo cargado
  directamente desde Cubicación), y cada tarea seleccionada se puede
  quitar del reporte de hoy con su botón "×" sin borrar sus datos. Un
  segundo tab, "Completadas", junto al de "Activas", lista todas las
  tareas que ya llegaron a su cantidad contratada — qué se hizo, en qué
  frente y en qué fecha se completaron —, así una tarea terminada deja
  el registro diario pero queda igual de consultable.
- **Avance de hoy aditivo + botón Terminado + dimensiones o metros
  faltantes** (`TareaActivaRow` en `NuevoPartePage.tsx`,
  `lib/cubicacionCalculo.ts`, `TareaDelDiaItem.dimensionesTexto` /
  `.faltanteLineal` en `queries.ts`): cada tarea activa en "Tareas y
  Avances" trae su propio campo "Avance de hoy" — anotar el avance ya no
  obliga a entrar a Cubicación. El campo es **puramente aditivo**: se
  escribe lo recién realizado (no el total del día) y el botón "+" (o
  Enter) lo suma sobre lo que ya llevabas registrado hoy, nunca lo
  reemplaza — así no hay que hacer el cálculo mental de "cuánto llevaba
  más cuánto hice ahora". Un texto discreto ("hoy llevas X unidad")
  muestra lo acumulado del día una vez que hay algo cargado. El botón
  "Terminado" (visible cuando la tarea está cubicada y bajo 100%) suma
  de una vez lo que falta para llegar exactamente a la cantidad
  contratada, con lo que la tarea pasa a "Completadas" al toque
  siguiente. Debajo de cada tarea, según su unidad: si **no es lineal**
  (m³, m², kg) se muestra un resumen compacto de las dimensiones de su
  última medición registrada con la calculadora (p. ej. "Dimensiones:
  3,2 × 1,8 × 0,8 m ×4"); si **es lineal** (ml) se muestra en cambio
  "Faltan N ml por completar" (`contratado − acumulado`), que es el dato
  que de verdad importa para una partida medida en metros lineales.
  `formatDimensionesCompacto()` y el resto de la lógica de tipos de
  elemento se movieron de `CubicacionPage.tsx` a
  `lib/cubicacionCalculo.ts` para que ambas pantallas compartan la misma
  implementación sin duplicarla. La página de Cubicación conserva su
  propio campo "Ejecutado hoy" de reemplazo directo, para corregir un
  valor a mano cuando haga falta.

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
- [ ] Seguimiento de maquinaria y equipo (no implementado; la app de
      referencia del rediseño tiene esas secciones en su navegación
      inferior, pero no forman parte del alcance actual).
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
