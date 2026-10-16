package cl.sacyr.rutaitata.excavaciones.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Coordenadas capturadas para una excavación. */
data class Coordenadas(val latitud: Double, val longitud: Double, val precision: Float?)

sealed interface ResultadoUbicacion {
    data class Lista(val coordenadas: Coordenadas) : ResultadoUbicacion
    data object SinPermiso : ResultadoUbicacion
    data object Desactivada : ResultadoUbicacion
    data object SinSenal : ResultadoUbicacion
}

/**
 * Una lectura de ubicación cuando se pide (botón "Obtener ubicación"), sin seguimiento continuo y
 * sin Google Play Services: usa el GPS del teléfono, o la red si el GPS está apagado. En terreno
 * abierto el GPS suele dar 3-10 m; la primera lectura del día puede tardar hasta ~30 s.
 */
class ProveedorUbicacion(context: Context) {

    private val context = context.applicationContext
    private val lm = context.getSystemService(LocationManager::class.java)

    fun tienePermiso(): Boolean = PERMISOS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // Se revisa con tienePermiso().
    suspend fun obtener(): ResultadoUbicacion {
        if (!tienePermiso()) return ResultadoUbicacion.SinPermiso
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) return ResultadoUbicacion.Desactivada
        val proveedor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return ResultadoUbicacion.Desactivada
        // getCurrentLocation se rinde solo a los ~30 s y entrega null.
        val lectura: Location? = suspendCancellableCoroutine { cont ->
            val cancelar = CancellationSignal()
            cont.invokeOnCancellation { cancelar.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, proveedor, cancelar, ContextCompat.getMainExecutor(context)) {
                if (cont.isActive) cont.resume(it)
            }
        }
        return lectura?.let { ResultadoUbicacion.Lista(Coordenadas(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null)) }
            ?: ResultadoUbicacion.SinSenal
    }

    companion object {
        val PERMISOS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
