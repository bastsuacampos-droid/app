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
- **Incremento atómico + cubicar tarea sin salir de Nuevo Parte**
  (`incrementarCubicacionEntry()` en `queries.ts`): el "+" de "Avance de
  hoy" ya no calcula el nuevo total a partir del último estado leído por
  React (`entriesHoy`) — relee el valor vigente directamente desde Dexie
  dentro de una transacción `rw` y recién ahí suma, así que dos toques
  seguidos nunca pueden pisarse ni perder un incremento por una carrera
  entre lectura y escritura (probado con 5 sumas de golpe sin esperar
  entre ellas: el total queda exacto). Además, una tarea agregada sin
  cantidad contratada ("sin cubicar aún") ya no se queda atascada —
  "Total ... · cubicar esta tarea →" abre un campo para fijar la
  cantidad ahí mismo, sin ir a Cubicación; recién con esa cifra puesta
  tienen sentido el % de avance y el botón "Terminado", que solo
  aparece — y solo puede completar la tarea — una vez que existe una
  cantidad contratada real contra la cual comparar el acumulado.
- **Decimales con coma en toda cantidad tipeada** (`lib/numero.ts`,
  `parseNumeroDecimal()`): el causante real de varias rondas de "el avance
  sigue mal" era `<input type="number">` — ese tipo de input solo acepta
  "." como separador decimal y descarta en silencio cualquier "," tipeada,
  así que "12,5" (la notación normal en español) quedaba como "125", diez
  veces más grande, sin ningún aviso. Todo campo de cantidad de libre
  tipeo (Ejecutado hoy, Cantidad contratada, Avance de hoy, largo/ancho/alto
  de la calculadora, etc.) usa `<input type="text" inputMode="decimal">` +
  `parseNumeroDecimal()`, que acepta "," o "." indistintamente. Un segundo
  cuidado va con esto: si el `value` de un input así se ata directamente a
  un estado numérico (o a un valor que viene de Dexie por `useLiveQuery`)
  que se reformatea en cada `onChange`, el input "se reescribe" a mitad de
  tipeo y borra la coma antes de que llegue el siguiente dígito — tipear
  "5,5" queda en "55". Por eso el valor mostrado siempre vive en un buffer
  de texto local (lo que el usuario tipeó, tal cual) y solo se convierte a
  número al confirmar o al usarlo en un cálculo; cuando ese buffer además
  debe reflejar cambios hechos desde otro lado (p. ej. "Ejecutado hoy" en
  `CubicacionPage.tsx` cuando la calculadora agrega un elemento), se
  resincroniza desde Dexie solo mientras el campo no está enfocado, para
  no pisar lo que el usuario está escribiendo en ese momento.
- **El "." como separador de miles, no solo la "," como decimal**
  (`parseNumeroDecimal()` en `lib/numero.ts`): arreglar la coma decimal no
  fue suficiente — la app misma muestra las cantidades con
  `toLocaleString('es-CL')` (p. ej. "4.200 m³"), así que es natural que
  alguien escriba "1.000" para anotar mil, con el mismo punto que ve en
  pantalla. Un reemplazo ingenuo de "," por "." antes de `parseFloat`
  interpretaba ese punto como decimal: "1.000" quedaba en 1, mil veces
  menos. Ahora, si el texto no tiene coma, un "." solo se trata como
  separador de miles (y se elimina) cuando lo que sigue es un grupo de
  exactamente 3 dígitos con grupos previos válidos (así "1.000" y
  "4.200" dan 1000 y 4200); si no, se trata como punto decimal normal
  (así "12.5" sigue dando 12,5). Con coma en el texto, cualquier "."
  anterior se asume de todos modos como separador de miles y se
  descarta antes de convertir la coma en el punto decimal (p. ej.
  "1.234,5" → 1234,5).
- **Total contratado visible junto al acumulado**: la línea de resumen de
  cada tarea en "Tareas y Avances" (`TareaActivaRow`) pasó de "Total X
  unidad (Avance Y%)" — donde X era el acumulado, no el total real — a "X
  de Y unidad (Avance Z%)", usando `t.contratado` (ya calculado en
  `queries.ts`), para ver de un vistazo cuánto falta sin entrar a
  Cubicación.
- **Clima por GPS** (`lib/clima.ts`, `obtenerClimaPorGPS()`): el botón
  "Usar clima por GPS" junto al selector de Clima pide la ubicación del
  dispositivo (`navigator.geolocation`) y consulta el clima actual en
  Open-Meteo (`api.open-meteo.com`), una API gratuita sin API key —
  encaja con el enfoque local-only de la app porque no depende de un
  backend propio, solo de una consulta directa desde el navegador. El
  código WMO que devuelve (`weather_code`) se reduce a los tres estados
  que maneja la app (`climaDesdeCodigoWMO()`: 0-1 → soleado, 2-3-45-48 →
  nublado, cualquier otro — llovizna, lluvia, nieve, tormenta — →
  lluvia) y junto con la temperatura se escriben con un solo `patch()`.
  Cualquier falla (sin permiso de ubicación, sin conexión, respuesta
  incompleta) se atrapa y se muestra como un mensaje en español al lado
  del botón, dejando los controles manuales de Clima/°C intactos como
  respaldo — nunca bloquea el flujo. **Ojo con el Artifact/preview de
  claude.ai**: ese sandbox bloquea fetch a hosts externos salvo Google
  Fonts, así que ahí el botón siempre va a mostrar el error de conexión;
  funciona normal corriendo la app fuera de ese sandbox (`npm run dev`,
  o donde sea que quede alojada para uso real).

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
