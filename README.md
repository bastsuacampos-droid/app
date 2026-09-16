# Mis Rendiciones

Aplicación web para llevar el control de gastos que se rinden ante un jefe o empresa: cuánto dinero te entregan, cuánto gastas, y si el saldo queda a tu favor (pusiste plata propia) o en contra (te sobró plata y hay que devolverla).

## Funcionalidades

- **Rendiciones**: abre una rendición (periodo de gastos), agrega boletas durante ese periodo, ciérrala cuando termine y abre la siguiente. El historial completo queda guardado.
- **Contador de saldo**: cada rendición muestra en tiempo real el saldo entre lo aportado por la empresa y lo gastado, indicando claramente si el saldo es a tu favor o en contra.
- **Captura de boletas con la cámara**: al agregar un gasto puedes tomar una foto de la boleta/factura directamente desde el celular.
- **Reconocimiento automático (OCR)**: la foto se procesa en el mismo dispositivo (sin subir la imagen a ningún servidor) usando Tesseract.js, y la app intenta extraer automáticamente el monto, la fecha y el comercio, además de sugerir la categoría del gasto (alimentación, transporte, combustible, alojamiento, oficina, comunicaciones, salud, otros) según palabras clave. Todos los datos quedan editables antes de guardar.
- **Resumen general**: estadísticas acumuladas de todas las rendiciones — total aportado, total gastado, cuánto has puesto de tu bolsillo, y un gráfico de gastos por categoría.
- **Funciona sin conexión**: los datos (incluidas las fotos de las boletas) se guardan localmente en el dispositivo (IndexedDB), no requieren backend ni conexión a internet, salvo la primera vez que se usa el reconocimiento automático de boletas (necesita descargar el motor de OCR).
- Instalable como aplicación (PWA) desde el navegador del celular.

## Cómo funciona el saldo

`Saldo = Total aportado por la empresa − Total gastado`

- Si gastaste más de lo que te dieron → **saldo a tu favor** (la empresa te debe).
- Si gastaste menos de lo que te dieron → **debes devolver la diferencia**.

## Stack técnico

- React + TypeScript + Vite
- Tailwind CSS
- Dexie (IndexedDB) para persistencia local, incluyendo las imágenes de las boletas
- Tesseract.js para OCR en el navegador
- Recharts para los gráficos del resumen
- React Router (HashRouter, para que funcione en cualquier hosting estático)

## Desarrollo

```bash
npm install
npm run dev      # servidor de desarrollo
npm run build    # build de producción (carpeta dist/)
```

Al ser una aplicación 100% cliente, `dist/` se puede publicar en cualquier hosting de archivos estáticos (GitHub Pages, Netlify, Vercel, etc.).
