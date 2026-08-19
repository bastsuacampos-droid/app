/**
 * Catálogo sugerido de partidas típicas de obras viales y obras de arte, agrupado por
 * categoría, para agregar una tarea nueva más rápido en vez de escribirla siempre a mano.
 *
 * Es un punto de partida general (nombres y unidades habituales en construcción vial en
 * Chile) — no es una transcripción literal de los códigos de partida del Manual de
 * Carreteras ni de un presupuesto específico. Ajusta nombres, unidades y códigos según el
 * contrato real de tu proyecto; puedes editar o borrar cualquier partida ya creada.
 */

export interface ItemCatalogo {
  nombre: string;
  unidad: string;
}

export interface CategoriaCatalogo {
  categoria: string;
  items: ItemCatalogo[];
}

export const CATALOGO_PARTIDAS: CategoriaCatalogo[] = [
  {
    categoria: 'Movimiento de tierras',
    items: [
      { nombre: 'Excavación en corte', unidad: 'm³' },
      { nombre: 'Excavación en roca', unidad: 'm³' },
      { nombre: 'Relleno estructural', unidad: 'm³' },
      { nombre: 'Terraplén', unidad: 'm³' },
      { nombre: 'Escarpe y limpieza de terreno', unidad: 'm²' },
      { nombre: 'Perfilado de taludes', unidad: 'm²' },
    ],
  },
  {
    categoria: 'Sub-base y base',
    items: [
      { nombre: 'Sub-base granular', unidad: 'm³' },
      { nombre: 'Base granular', unidad: 'm³' },
      { nombre: 'Estabilizado granular', unidad: 'm³' },
    ],
  },
  {
    categoria: 'Pavimentos',
    items: [
      { nombre: 'Imprimación asfáltica', unidad: 'm²' },
      { nombre: 'Riego de liga', unidad: 'm²' },
      { nombre: 'Carpeta de rodadura asfáltica', unidad: 'm³' },
      { nombre: 'Pavimento de hormigón', unidad: 'm²' },
      { nombre: 'Sello asfáltico', unidad: 'm²' },
    ],
  },
  {
    categoria: 'Obras de arte y drenaje',
    items: [
      { nombre: 'Alcantarilla de tubo', unidad: 'ml' },
      { nombre: 'Cabezal de alcantarilla', unidad: 'un' },
      { nombre: 'Cuneta de hormigón', unidad: 'ml' },
      { nombre: 'Foso o canal de drenaje', unidad: 'ml' },
      { nombre: 'Losa de aproximación', unidad: 'm²' },
      { nombre: 'Junta de dilatación', unidad: 'ml' },
      { nombre: 'Bajada de agua', unidad: 'un' },
    ],
  },
  {
    categoria: 'Hormigones y estructuras',
    items: [
      { nombre: 'Hormigón estructural', unidad: 'm³' },
      { nombre: 'Enfierradura', unidad: 'kg' },
      { nombre: 'Moldaje', unidad: 'm²' },
      { nombre: 'Muro de contención de hormigón', unidad: 'm³' },
      { nombre: 'Muro de gaviones', unidad: 'm³' },
      { nombre: 'Muro de suelo reforzado', unidad: 'm²' },
    ],
  },
  {
    categoria: 'Señalización y seguridad vial',
    items: [
      { nombre: 'Señal vertical', unidad: 'un' },
      { nombre: 'Demarcación de pavimento', unidad: 'ml' },
      { nombre: 'Barrera de seguridad', unidad: 'ml' },
      { nombre: 'Defensa camineras (New Jersey)', unidad: 'ml' },
    ],
  },
];
