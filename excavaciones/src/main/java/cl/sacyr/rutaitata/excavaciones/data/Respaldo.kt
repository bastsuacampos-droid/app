package cl.sacyr.rutaitata.excavaciones.data

import androidx.room.withTransaction
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FotoEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FrenteEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Lo que trae un respaldo, para mostrarlo antes de restaurar. */
data class ResumenRespaldo(val creadoEn: Long, val excavaciones: Int, val fotos: Int, val desde: LocalDate?, val hasta: LocalDate?)

class RespaldoInvalido(mensaje: String) : IOException(mensaje)

/**
 * Respaldo completo de la app en un ZIP: `respaldo.json` (todas las tablas) y `fotos/<archivo>`.
 *
 * Es un respaldo lógico (no una copia del archivo de la base): no depende de que la base esté
 * abierta ni de su versión interna, y un respaldo de una versión anterior de la app se puede
 * restaurar en una nueva. Restaurar **reemplaza** todo lo que hay en el teléfono.
 *
 * Formato: subir [FORMATO] solo si un respaldo nuevo no se puede leer con el código anterior;
 * los campos que se agreguen deben ser opcionales al leer.
 */
class Respaldo(private val bd: BaseDatos, private val fotos: ArchivosFotos) {

    /** Escribe el respaldo en [salida] (no la cierra). Devuelve cuántas excavaciones y fotos lleva. */
    suspend fun exportar(salida: OutputStream, ahora: Long = System.currentTimeMillis()): ResumenRespaldo {
        val r = bd.respaldo()
        // Una sola lectura consistente: nadie escribe entre una tabla y otra.
        val json = bd.withTransaction {
            JSONObject()
                .put("app", APP)
                .put("formato", FORMATO)
                .put("creadoEn", ahora)
                .put("tiposTerreno", JSONArray(r.tiposTerreno().map(::tipoTerrenoAJson)))
                .put("sectores", JSONArray(r.sectores().map(::sectorAJson)))
                .put("frentes", JSONArray(r.frentes().map(::frenteAJson)))
                .put("tiposExcavacion", JSONArray(r.tiposExcavacion().map(::tipoExcavacionAJson)))
                .put("excavaciones", JSONArray(r.excavaciones().map(::excavacionAJson)))
                .put("fotos", JSONArray(r.fotos().map(::fotoAJson)))
        }
        val zip = ZipOutputStream(salida)
        zip.putNextEntry(ZipEntry(JSON))
        zip.write(json.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        val listaFotos = json.getJSONArray("fotos")
        var conArchivo = 0
        for (i in 0 until listaFotos.length()) {
            val archivo = fotos.archivo(listaFotos.getJSONObject(i).getString("archivo"))
            if (!archivo.isFile) continue // borrada a mano: el registro se respalda igual
            zip.putNextEntry(ZipEntry(CARPETA_FOTOS + archivo.name))
            archivo.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            conArchivo++
        }
        zip.finish()
        return resumen(json).copy(fotos = conArchivo)
    }

    /** Lee solo el resumen (va al comienzo del ZIP), sin tocar nada. */
    fun leerResumen(entrada: InputStream): ResumenRespaldo = resumen(leerJson(ZipInputStream(entrada)))

    /**
     * Reemplaza todo el contenido del teléfono por el del respaldo. Si algo falla (archivo dañado,
     * de otra app, incompleto), no se toca nada.
     */
    suspend fun restaurar(entrada: InputStream): ResumenRespaldo {
        val zip = ZipInputStream(entrada)
        val json = leerJson(zip)
        val temporal = File(fotos.carpeta, "restaurando").apply { deleteRecursively(); mkdirs() }
        try {
            // Primero se copian las fotos a una carpeta aparte: si el ZIP viene cortado, no se pierde nada.
            while (true) {
                val e = zip.nextEntry ?: break
                val nombre = e.name.removePrefix(CARPETA_FOTOS)
                if (e.isDirectory || !e.name.startsWith(CARPETA_FOTOS) || !NOMBRE_SEGURO.matches(nombre)) continue
                File(temporal, nombre).outputStream().use { zip.copyTo(it) }
            }
            val tipos = json.lista("tiposTerreno", ::tipoTerrenoDeJson)
            val sectores = json.lista("sectores", ::sectorDeJson)
            val frentes = json.lista("frentes", ::frenteDeJson)
            val tiposExc = json.lista("tiposExcavacion", ::tipoExcavacionDeJson)
            val excavaciones = json.lista("excavaciones", ::excavacionDeJson)
            val registrosFotos = json.lista("fotos", ::fotoDeJson)

            val r = bd.respaldo()
            bd.withTransaction {
                r.borrarFotos(); r.borrarExcavaciones(); r.borrarTiposExcavacion()
                r.borrarFrentes(); r.borrarSectores(); r.borrarTiposTerreno()
                r.insertarTiposTerreno(tipos); r.insertarSectores(sectores); r.insertarFrentes(frentes)
                r.insertarTiposExcavacion(tiposExc); r.insertarExcavaciones(excavaciones); r.insertarFotos(registrosFotos)
            }
            // La base ya cambió: las fotos pasan a su lugar y se borran las que ya no usa nadie.
            temporal.listFiles()?.forEach { it.copyTo(fotos.archivo(it.name), overwrite = true) }
            val enUso = registrosFotos.map { it.archivo }.toSet()
            fotos.carpeta.listFiles()?.filter { it.isFile && it.name !in enUso }?.forEach { it.delete() }
            return resumen(json)
        } catch (e: org.json.JSONException) {
            throw RespaldoInvalido("El respaldo está dañado (${e.message})")
        } finally {
            temporal.deleteRecursively()
        }
    }

    private fun leerJson(zip: ZipInputStream): JSONObject {
        val primera = zip.nextEntry
        if (primera == null || primera.name != JSON) throw RespaldoInvalido("No es un respaldo de la app de excavaciones")
        val json = try {
            JSONObject(zip.readBytes().toString(Charsets.UTF_8))
        } catch (e: org.json.JSONException) {
            throw RespaldoInvalido("El respaldo está dañado")
        }
        if (json.optString("app") != APP) throw RespaldoInvalido("No es un respaldo de la app de excavaciones")
        if (json.optInt("formato") > FORMATO) throw RespaldoInvalido("El respaldo es de una versión más nueva de la app: actualízala primero")
        return json
    }

    private fun resumen(json: JSONObject): ResumenRespaldo {
        val exc = json.getJSONArray("excavaciones")
        val fechas = (0 until exc.length()).map { LocalDate.ofEpochDay(exc.getJSONObject(it).getLong("fecha")) }
        return ResumenRespaldo(
            creadoEn = json.getLong("creadoEn"),
            excavaciones = exc.length(),
            fotos = json.getJSONArray("fotos").length(),
            desde = fechas.minOrNull(),
            hasta = fechas.maxOrNull(),
        )
    }

    private fun <T> JSONObject.lista(clave: String, leer: (JSONObject) -> T): List<T> {
        val a = optJSONArray(clave) ?: return emptyList()
        return (0 until a.length()).map { leer(a.getJSONObject(it)) }
    }

    companion object {
        const val APP = "excavaciones-ruta-itata"
        const val FORMATO = 1
        private const val JSON = "respaldo.json"
        private const val CARPETA_FOTOS = "fotos/"
        /** Solo nombres simples: nada de "../" en un ZIP manipulado. */
        private val NOMBRE_SEGURO = Regex("[A-Za-z0-9._-]{1,120}")

        // JSONObject.NULL para los null: put(clave, null) borraría la clave.
        private fun JSONObject.ponerOpcional(clave: String, valor: Any?): JSONObject = put(clave, valor ?: JSONObject.NULL)
        private fun JSONObject.doble(clave: String): Double? = if (isNull(clave)) null else getDouble(clave)
        private fun JSONObject.largo(clave: String): Long? = if (isNull(clave)) null else getLong(clave)
        private fun JSONObject.texto(clave: String): String? = if (isNull(clave)) null else getString(clave)

        private fun tipoTerrenoAJson(t: TipoTerrenoEntity) = JSONObject()
            .put("id", t.id).put("nombre", t.nombre).put("orden", t.orden).put("activo", t.activo)
        private fun tipoTerrenoDeJson(o: JSONObject) =
            TipoTerrenoEntity(o.getLong("id"), o.getString("nombre"), o.getInt("orden"), o.optBoolean("activo", true))

        private fun sectorAJson(s: SectorEntity) = JSONObject().put("id", s.id).put("nombre", s.nombre).put("creadoEn", s.creadoEn)
        private fun sectorDeJson(o: JSONObject) = SectorEntity(o.getLong("id"), o.getString("nombre"), o.getLong("creadoEn"))

        private fun frenteAJson(f: FrenteEntity) = JSONObject()
            .put("id", f.id).put("sectorId", f.sectorId).put("lado", f.lado.codigo).put("sentido", f.sentido.codigo)
        private fun frenteDeJson(o: JSONObject) = FrenteEntity(
            o.getLong("id"), o.getLong("sectorId"), Lado.desdeCodigo(o.getString("lado")), Sentido.desdeCodigo(o.getString("sentido")),
        )

        private fun tipoExcavacionAJson(t: TipoExcavacionEntity) = JSONObject()
            .put("id", t.id).put("nombre", t.nombre)
            .ponerOpcional("largo", t.largo).ponerOpcional("ancho", t.ancho).ponerOpcional("profundidad", t.profundidad)
            .ponerOpcional("tipoTerrenoId", t.tipoTerrenoId).ponerOpcional("observaciones", t.observaciones)
        private fun tipoExcavacionDeJson(o: JSONObject) = TipoExcavacionEntity(
            o.getLong("id"), o.getString("nombre"), o.doble("largo"), o.doble("ancho"), o.doble("profundidad"),
            o.largo("tipoTerrenoId"), o.texto("observaciones"),
        )

        private fun excavacionAJson(e: ExcavacionEntity) = JSONObject()
            .put("id", e.id).put("fecha", e.fecha.toEpochDay()).put("frenteId", e.frenteId).put("numero", e.numero)
            .ponerOpcional("progresiva", e.progresiva)
            .put("largo", e.largo).put("ancho", e.ancho).put("profundidad", e.profundidad)
            .ponerOpcional("tipoTerrenoId", e.tipoTerrenoId)
            .ponerOpcional("latitud", e.latitud).ponerOpcional("longitud", e.longitud)
            .ponerOpcional("precisionGps", e.precisionGps?.toDouble())
            .ponerOpcional("observaciones", e.observaciones)
            .put("creadoEn", e.creadoEn).put("actualizadoEn", e.actualizadoEn)
        private fun excavacionDeJson(o: JSONObject) = ExcavacionEntity(
            id = o.getLong("id"),
            fecha = LocalDate.ofEpochDay(o.getLong("fecha")),
            frenteId = o.getLong("frenteId"),
            numero = o.getInt("numero"),
            progresiva = o.texto("progresiva"),
            largo = o.getDouble("largo"),
            ancho = o.getDouble("ancho"),
            profundidad = o.getDouble("profundidad"),
            tipoTerrenoId = o.largo("tipoTerrenoId"),
            latitud = o.doble("latitud"),
            longitud = o.doble("longitud"),
            precisionGps = o.doble("precisionGps")?.toFloat(),
            observaciones = o.texto("observaciones"),
            creadoEn = o.getLong("creadoEn"),
            actualizadoEn = o.getLong("actualizadoEn"),
        )

        private fun fotoAJson(f: FotoEntity) = JSONObject()
            .put("id", f.id).put("excavacionId", f.excavacionId).put("orden", f.orden).put("archivo", f.archivo)
            .ponerOpcional("tomadaEn", f.tomadaEn)
        private fun fotoDeJson(o: JSONObject) =
            FotoEntity(o.getLong("id"), o.getLong("excavacionId"), o.getInt("orden"), o.getString("archivo"), o.largo("tomadaEn"))
    }
}
