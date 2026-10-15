package cl.sacyr.rutaitata.excavaciones.data

import android.content.Context
import androidx.core.content.edit
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido

/** Frente con que se abre un formulario nuevo: el último usado, para registrar en serie. */
data class UltimoFrente(val sector: String, val lado: Lado?, val sentido: Sentido?, val tipoTerrenoId: Long?)

/** Lo que el teléfono recuerda entre usos (no son registros de obra: esos van en la base). */
class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    var responsable: String
        get() = prefs.getString(RESPONSABLE, "").orEmpty()
        set(valor) = prefs.edit { putString(RESPONSABLE, valor.trim()) }

    var incluirFotos: Boolean
        get() = prefs.getBoolean(INCLUIR_FOTOS, true)
        set(valor) = prefs.edit { putBoolean(INCLUIR_FOTOS, valor) }

    /** Último tipo de excavación elegido: se aplica solo al abrir una excavación nueva. */
    var ultimoTipoExcavacion: Long?
        get() = prefs.getLong(TIPO_EXCAVACION, -1).takeIf { it > 0 }
        set(id) = prefs.edit { putLong(TIPO_EXCAVACION, id ?: -1) }

    var ultimoFrente: UltimoFrente
        get() = UltimoFrente(
            sector = prefs.getString(SECTOR, "").orEmpty(),
            lado = prefs.getString(LADO, null)?.let { c -> Lado.entries.firstOrNull { it.codigo == c } },
            sentido = prefs.getString(SENTIDO, null)?.let { c -> Sentido.entries.firstOrNull { it.codigo == c } },
            tipoTerrenoId = prefs.getLong(TIPO, -1).takeIf { it > 0 },
        )
        set(f) = prefs.edit {
            putString(SECTOR, f.sector)
            putString(LADO, f.lado?.codigo)
            putString(SENTIDO, f.sentido?.codigo)
            putLong(TIPO, f.tipoTerrenoId ?: -1)
        }

    private companion object {
        const val RESPONSABLE = "responsable"
        const val INCLUIR_FOTOS = "incluir_fotos"
        const val SECTOR = "ultimo_sector"
        const val LADO = "ultimo_lado"
        const val SENTIDO = "ultimo_sentido"
        const val TIPO = "ultimo_tipo_terreno"
        const val TIPO_EXCAVACION = "ultimo_tipo_excavacion"
    }
}
