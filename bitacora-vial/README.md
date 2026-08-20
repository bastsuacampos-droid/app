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

## Compilar como app Android (Capacitor)

El proyecto ya trae [Capacitor](https://capacitorjs.com/) instalado y
configurado (`capacitor.config.ts`, `appId: cl.bitacoravial.app`) con la
carpeta nativa `android/` generada por `npx cap add android`, incluyendo:

- **Íconos y splash screen** ya generados a partir de
  `public/icons/icon-512.png` (con `@capacitor/assets`, ver `assets/icon.png`)
  para todas las densidades (`mipmap-ldpi` … `mipmap-xxxhdpi`), en modo claro
  y oscuro.
- **Permisos** agregados a mano en `android/app/src/main/AndroidManifest.xml`
  para lo que la app realmente usa dentro del WebView: `CAMERA` (fotografiar
  avances, `<input type="file" capture>`) y `ACCESS_FINE_LOCATION` /
  `ACCESS_COARSE_LOCATION` (clima por GPS, ver `ConfiguracionPage` /
  `permisoUbicacion`) — Capacitor no los agrega solo porque la app no usa los
  plugins nativos `@capacitor/camera` ni `@capacitor/geolocation`, solo las
  APIs web estándar que su WebView bridge ya sabe intermediar cuando el
  manifiesto declara el permiso.

**Nota sobre este entorno:** compilar el `.apk` requiere el Android SDK
(plataforma + build-tools), que `sdkmanager` descarga desde `dl.google.com` —
host bloqueado por la política de red de este entorno en la nube (403 al
intentar el `CONNECT`). Por eso el proyecto queda armado y lo que falta es
correrlo en un lugar con ese acceso: tu máquina con Android Studio, o
cualquier entorno con `ANDROID_HOME` configurado.

Para compilar una vez tengas el SDK disponible:

```bash
# Opción 1: Android Studio (abre el proyecto android/ y compila desde ahí)
npm run android:openStudio

# Opción 2: línea de comandos, requiere Android SDK + ANDROID_HOME
npm run android:assembleDebug
# genera android/app/build/outputs/apk/debug/app-debug.apk
```

Ambos comandos corren primero `npm run cap:sync` (build web + `npx cap sync
android`), que copia el último `dist/` a `android/app/src/main/assets/public`
— siempre hay que correrlo después de cambiar el código web y antes de volver
a compilar el APK, si no la app nativa queda con una versión vieja adentro.

El APK de `assembleDebug` está firmado con una clave de debug genérica (no
apta para publicar en Play Store, pero perfectamente instalable directo en un
teléfono vía `adb install` o copiando el archivo). Para una versión firmada
para distribuir fuera de Play Store hace falta generar un keystore propio y
configurar `android/app/build.gradle` con `signingConfigs` — no incluido
todavía porque es un paso que depende de decisiones del usuario (alias,
contraseña, dónde guardar el keystore) que no correspondía tomar por él.

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
- **Figura con cotas en la calculadora** (`FiguraMedidas.tsx`): arriba de
  los campos de largo/ancho/alto (etc.) de cada tipo de elemento aparece
  un dibujo esquemático de la figura — prisma, trapecio, cilindro, muro
  con vano, línea o barra de enfierradura — con cotas (líneas de cota con
  flechas, como en un plano) que muestran en vivo lo que el usuario va
  tipeando en cada campo ("3,2 m", "—" mientras está vacío), para que
  quede claro de un vistazo a qué medida corresponde cada campo antes de
  guardar. Las proporciones del dibujo son fijas y solo ilustrativas, no
  a escala real de las cifras — es una ayuda visual, no un plano técnico.
  Un badge "×N" aparece en la esquina cuando "Cantidad" es mayor a 1. Se
  reutiliza igual en "Cantidad contratada", "Ejecutado hoy" y
  "Cubicar esta tarea", porque las tres pasan por el mismo
  `CalculadoraCubicacion`.
  **Sobre las cotas:** no son un calco de fotos de planos reales —las
  herramientas de este entorno solo leen texto/markdown de una URL, no
  bajan ni recortan píxeles de una imagen— sino que siguen la
  convención real de acotado de planos de construcción, confirmada por
  investigación (líneas de cota con puntas de flecha alargadas ~3:1,
  líneas testigo que salen del borde del objeto con un pequeño espacio
  y sobrepasan un poco la línea de cota, y achurado diagonal en el
  material sólido —dejando en blanco los vanos, como en un plano real).
  Sigue siendo SVG vectorial puro (nada de imágenes rasterizadas), así
  que el peso agregado al bundle es mínimo (~7 KB comprimido) frente a
  lo que costaría incrustar fotos de referencia.
  Cada figura además vive dentro de un **marco de hoja de plano**, con
  una franja de "cajetín" abajo (nombre de la vista a la izquierda —
  "Corte transversal", "Vista isométrica — Prisma rectangular", etc. —
  y "S/ESC." a la derecha, la abreviatura estándar de "sin escala"),
  igual que cualquier plano de construcción real identifica cada
  detalle. El objetivo es que se lea de inmediato como un dibujo
  técnico del rubro, no como un ícono genérico.
- **"Ver dibujo" en la memoria de cálculo** (`camposDesdeDatos()` en
  `lib/cubicacionCalculo.ts`): la lista colapsable "Ver mediciones" de
  cada tarea (`MemoriaCalculo` en `CubicacionPage.tsx`) solo mostraba el
  resumen en texto de cada medición pasada ("Largo: 3,2 · Ancho: 1,8 ·
  Alto: 0,8"), sin la figura. Ahora cada medición trae su propio botón
  "Ver dibujo" que despliega justo debajo la misma `FiguraMedidas` con
  cotas de la calculadora, reconstruida a partir de lo que esa medición
  guardó (`MedicionCubicacion.tipo` / `.datos` / `.unidad`) — así se
  puede volver a revisar de qué forma salió una cifra semanas después,
  no solo el número. `camposDesdeDatos()` convierte los `datos:
  Record<string, number>` guardados de vuelta al `campos: Record<string,
  string>` que `FiguraMedidas` espera, formateando cada valor con
  `toLocaleString('es-CL')`.
- **"Ver medidas" en Nuevo Parte Diario** (`TareaActivaRow` en
  `NuevoPartePage.tsx`): la misma capacidad de la memoria de cálculo,
  pero directamente en la tarjeta de cada tarea de la sección 3 ("Tareas
  y Avances"), para no tener que salir a Cubicación solo para recordar
  con qué medidas se cubicó. El botón se muestra siempre que la unidad
  no sea `ml` — no solo cuando ya hay un texto "Dimensiones: ..." —
  porque una tarea puede estar cubicada con la cantidad contratada
  tipeada directo (sin pasar por la calculadora), y ese caso también
  necesita el botón, solo que al abrirlo explica "Esta tarea no tiene
  medidas registradas — se cubicó ingresando la cantidad directamente."
  en vez de un dibujo (bug encontrado por el usuario en la primera
  versión de esta función, que solo lo mostraba cuando ya había una
  medición guardada). Cuando sí hay una, trae la más reciente de esa
  partida con `medicionesDePartida()` — vía `useLiveQuery`, y solo
  mientras el dibujo está abierto, para no consultar Dexie de más en una
  pantalla con varias tareas a la vez — y dibuja su `FiguraMedidas` con
  `camposDesdeDatos()`, igual que en Cubicación. Las tareas de unidad
  `ml` siguen mostrando "Faltan N ml por completar" en vez de esto,
  porque una longitud simple no tiene geometría que dibujar.
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
- **"Retomar pendiente" y "Cubicar nueva tarea" como botones separados,
  cada uno acotado a un frente** (`frenteActualId` en
  `NuevoPartePage.tsx`, `TareaCandidata.frenteId` en `queries.ts`): el
  botón único "Seleccionar tarea" mezclaba en una sola lista las tareas
  pendientes de **todos** los frentes activos del día sin indicar a
  cuál pertenecía cada una — con dos frentes usando el mismo catálogo
  (p. ej. ambos con "Excavación en corte"), era fácil tocar la tarea
  equivocada y terminar anotando avance en el frente que no era. Ahora
  son dos botones: "Retomar pendiente" abre el listado de tareas sin
  terminar (igual que antes) pero acotado a un solo frente a la vez —
  si el parte tiene más de un frente activo, aparecen chips arriba del
  listado para elegir cuál, y cambiar de chip cambia el listado
  completo, nunca mezcla tareas de dos frentes en una misma pantalla;
  "Cubicar nueva tarea" quedó como botón directo a Cubicación, sin
  pasar primero por el desplegable.
- **"Cubicar nueva tarea" abre un modal dedicado, sin pasar por
  Cubicación** (`NuevaTareaModal.tsx`, `crearPartida()` en
  `queries.ts`): ese botón llegó a navegar a la pantalla completa de
  Cubicación con el formulario ya abierto y con scroll automático hasta
  él, pero seguía cargando de fondo el título "Cubicación de Tareas",
  el selector de frente, la tarjeta de avance y el listado de tareas
  existentes — información que "Retomar pendiente" ya mostraba, de más
  para el único propósito de este botón. Ahora abre un overlay de
  pantalla completa (`position: fixed`) encima de Nuevo Parte mismo, sin
  navegar a ninguna parte: solo el encabezado "Nueva tarea" + el nombre
  del frente + botón cerrar, y debajo el formulario (catálogo sugerido,
  nombre, unidad, cantidad contratada, calculadora, avance de hoy). Al
  guardar, la tarea queda creada bajo el frente en el que estaba parado
  en Nuevo Parte (`frenteActualId`) y se auto-selecciona en "Tareas y
  Avances" al cerrarse el modal, sin un paso extra. Para que Cubicación,
  el modal y el "Agregar sub-tarea" de Cubicación compartan exactamente
  la misma lógica de alta (crear la partida, loguear el avance de hoy si
  corresponde, guardar las mediciones de la calculadora) sin triplicar
  código, `NuevaPartidaForm` y `CalculadoraCubicacion` se movieron a sus
  propios archivos en `features/cubicacion/`, y el guardado en sí vive
  en `crearPartida()` (`lib/queries.ts`), que las tres puertas de
  entrada llaman por igual.
- **`CampoDesplegable` en vez de `<select>` nativo** (`NuevaPartidaForm.tsx`):
  el selector de "Catálogo sugerido" y el de unidad usaban `<select>`
  nativo, que en el celular abre el picker propio del sistema operativo
  (oscuro, con radio buttons) en vez de algo con la cara de la app —
  se nota especialmente en el catálogo, una lista larga. Ahora ambos
  usan el mismo patrón de desplegable a medida que ya existía en
  "Seleccionar punto de trabajo" de Nuevo Parte: un botón con pinta de
  `field-input` y una flecha que gira, y debajo una tarjeta con las
  opciones (la elegida resaltada en azul) que se cierra sola al tocar
  afuera. `CampoDesplegable` (`components/CampoDesplegable.tsx`) se
  movió después a un archivo compartido, con soporte para
  `{value, label}` (no solo texto plano) y estilo de botón
  personalizable, para poder reemplazar también el resto de los
  `<select>` nativos de la app — ver el punto siguiente.
- **Ronda de prueba como usuario nuevo — 4 errores reales encontrados y
  corregidos**: con IndexedDB limpia (perfil de navegador nuevo,
  onboarding desde cero) se recorrió toda la app de punta a punta
  (Playwright) buscando fallos que un capataz real se encontraría el
  primer día.
  - **Los partes empezaban en N° 119, no en N° 1** (`queries.ts`,
    `ensureTodayParteExists()`): el cálculo del siguiente número
    (`last?.numero ?? 118) + 1`) tenía un `118` de relleno, sembrado
    para que el mockup de diseño se viera con historial — pero quedó
    también en el código real, así que hasta la primera instalación de
    un usuario nuevo, sin partes previos, arrancaba en "N° 119". Ahora
    el valor de respaldo es `0`, así que el primer parte de cualquier
    instalación nueva es "N° 1".
  - **El selector de mes en Horas Extra mostraba el idioma del
    dispositivo, no español** (`HorasExtraPage.tsx`): un
    `<input type="month">` nativo rotula su propio picker según el
    idioma del navegador/SO, ignorando el `lang="es"` de la página —
    en un dispositivo en inglés se leía "August 2026" en medio de una
    app 100% en español. Se reemplazó por flechas
    anterior/siguiente + una etiqueta formateada con date-fns
    (`formatMonthLabel()`, que ya existía en el código pero no se
    usaba en ningún lado) — siempre en español, sin depender del
    idioma del dispositivo. Se agregó `shiftMonthISO()` a `lib/date.ts`
    para mover el mes ±1.
  - **"Nueva versión disponible" en Configuración era permanente y
    falsa** (`ConfiguracionPage.tsx`): `APP_VERSION` y `LATEST_VERSION`
    eran dos constantes hardcodeadas y distintas (`'1.0'` / `'1.1'`)
    que nunca podían converger, así que el aviso de actualización
    nunca se podía resolver — y encima anunciaba como "novedades" el
    reporte de horas extra, Documentos y Configuración, funciones que
    ya estaban corriendo en la versión que el usuario tenía abierta en
    ese momento. Tocar "Actualizar ahora" solo hacía
    `window.location.reload()`, que no cambiaba nada y mostraba el
    mismo aviso falso de nuevo. Se sacó la comparación de versiones —
    sin backend no hay contra qué comparar — y ahora muestra siempre
    "Estás al día · v1.0", honesto con lo que la app puede saber de sí
    misma.
  - **El onboarding y Configuración prometían "registrar la progresiva
    (Km) automáticamente" con el permiso de ubicación** — función que
    nunca se implementó (ni existe una forma realista de mapear GPS a
    kilometraje de una ruta sin datos de la geometría del camino). Se
    corrigió el texto en ambas pantallas para describir lo que el
    permiso de ubicación sí hace hoy: completar clima y temperatura
    del parte con el botón "Usar clima por GPS".
  - Aprovechando que ya estaba extendido, `CampoDesplegable` reemplazó
    también los `<select>` nativos restantes de mayor uso: el selector
    de frente en el header de Cubicación, "Nuevo archivo se guarda
    como" en Documentos, "Unidades de medida" en Configuración, y el
    selector de tarea al subir una foto en Fotos. El selector de "¿A
    qué frente lo prestas?" en Asistencia (préstamo de personal) queda
    nativo por ahora — es una acción secundaria de menor uso, no
    encontrada en este recorrido.

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
