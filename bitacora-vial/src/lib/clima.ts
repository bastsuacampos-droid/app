import type { Clima } from '../types/models';

/** Maps an Open-Meteo WMO weather code (https://open-meteo.com/en/docs) down to this app's
 * three simplified clima options. Anything with precipitation (drizzle, rain, snow, showers,
 * thunderstorm) counts as "lluvia" — that's what actually causes an "atraso por clima" on a
 * road jobsite, regardless of the exact WMO nuance. */
function climaDesdeCodigoWMO(codigo: number): Clima {
  if (codigo === 0 || codigo === 1) return 'soleado';
  if (codigo === 2 || codigo === 3 || codigo === 45 || codigo === 48) return 'nublado';
  return 'lluvia';
}

export interface ClimaGPS {
  clima: Clima;
  temperaturaC: number;
}

function obtenerPosicion(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('Este navegador no soporta ubicación GPS.'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      resolve,
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          reject(new Error('Permiso de ubicación denegado. Actívalo en el navegador para usar esta función.'));
        } else {
          reject(new Error('No se pudo obtener tu ubicación GPS.'));
        }
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 5 * 60 * 1000 },
    );
  });
}

/** Fetches current weather for the device's GPS position from Open-Meteo — a free weather API
 * that needs no API key/registration, which fits this app's local-only, no-backend-of-our-own
 * approach. Requires network + location permission; throws a Spanish, user-facing error on any
 * failure (denied permission, offline, bad response) so the caller can show it and let the
 * foreman fall back to picking clima/temperatura manually as before. */
export async function obtenerClimaPorGPS(): Promise<ClimaGPS> {
  const posicion = await obtenerPosicion();
  const { latitude, longitude } = posicion.coords;
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,weather_code&timezone=auto`;

  let respuesta: Response;
  try {
    respuesta = await fetch(url);
  } catch {
    throw new Error('No se pudo conectar al servicio de clima. Revisa tu conexión.');
  }
  if (!respuesta.ok) {
    throw new Error('El servicio de clima no respondió correctamente.');
  }

  const datos = await respuesta.json();
  const codigo = datos?.current?.weather_code;
  const temperatura = datos?.current?.temperature_2m;
  if (typeof codigo !== 'number' || typeof temperatura !== 'number') {
    throw new Error('El servicio de clima devolvió datos incompletos.');
  }

  return { clima: climaDesdeCodigoWMO(codigo), temperaturaC: Math.round(temperatura * 10) / 10 };
}
