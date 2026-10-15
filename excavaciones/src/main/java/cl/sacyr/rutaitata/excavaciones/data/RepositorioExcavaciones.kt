package cl.sacyr.rutaitata.excavaciones.data

import androidx.room.withTransaction
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FotoEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FrenteEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity
import cl.sacyr.rutaitata.excavaciones.dominio.Agrupador
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Campo
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionInvalida
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoSector
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import cl.sacyr.rutaitata.excavaciones.dominio.ValidadorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Volumen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Lo que muestra el dashboard para una fecha. */
data class ResumenDia(val fecha: LocalDate, val sectores: List<GrupoSector>, val excavaciones: List<ExcavacionDetalle>) {
    val cantidad: Int get() = excavaciones.size
    val volumen: Double get() = Volumen.total(excavaciones)
}

/**
 * Única puerta de la interfaz a los datos. Todo es local (offline-first): no hay servidor que
 * sincronizar, así que la base es la fuente de verdad y los Flow avisan cada cambio.
 */
class RepositorioExcavaciones(
    private val bd: BaseDatos,
    private val ahora: () -> Long = System::currentTimeMillis,
) {

    fun resumenDelDia(fecha: LocalDate): Flow<ResumenDia> =
        bd.excavaciones().observarDelDia(fecha).map { ResumenDia(fecha, Agrupador.agrupar(it), it) }

    fun sectores(): Flow<List<SectorEntity>> = bd.sectores().observarTodos()

    fun tiposTerreno(): Flow<List<TipoTerrenoEntity>> = bd.tiposTerreno().observarActivos()

    /** Para el reporte: entre dos fechas incluidas, de un sector o de todos. */
    suspend fun paraReporte(desde: LocalDate, hasta: LocalDate, sectorId: Long? = null): List<ExcavacionDetalle> =
        bd.excavaciones().entre(minOf(desde, hasta), maxOf(desde, hasta), sectorId)

    suspend fun excavacion(id: Long): ExcavacionEntity? = bd.excavaciones().porId(id)

    suspend fun fotos(excavacionId: Long): List<FotoEntity> = bd.fotos().de(excavacionId)

    /** Fotos de varias excavaciones (para el reporte), por id de excavación y en orden. */
    suspend fun fotosDe(excavaciones: Collection<Long>): Map<Long, List<FotoEntity>> =
        excavaciones.distinct().chunked(500) // SQLite limita la cantidad de parámetros por consulta
            .flatMap { bd.fotos().deVarias(it) }
            .groupBy { it.excavacionId }
            .mapValues { (_, fotos) -> fotos.sortedBy { it.orden } }

    /** Archivos de fotos que usa algún registro: el resto se puede borrar. */
    suspend fun archivosDeFotosEnUso(): Set<String> = bd.fotos().archivos().toSet()

    /** La excavación como la edita el formulario, o null si ya no existe. */
    suspend fun borrador(id: Long): BorradorExcavacion? {
        val e = bd.excavaciones().porId(id) ?: return null
        val frente = bd.frentes().porId(e.frenteId) ?: return null
        val sector = bd.sectores().porId(frente.sectorId) ?: return null
        return BorradorExcavacion(
            id = e.id,
            fecha = e.fecha,
            sector = sector.nombre,
            lado = frente.lado,
            sentido = frente.sentido,
            numero = e.numero,
            progresiva = e.progresiva,
            largo = e.largo,
            ancho = e.ancho,
            profundidad = e.profundidad,
            tipoTerrenoId = e.tipoTerrenoId,
            latitud = e.latitud,
            longitud = e.longitud,
            precisionGps = e.precisionGps,
            observaciones = e.observaciones,
            fotos = bd.fotos().de(id).map { FotoBorrador(it.archivo, it.tomadaEn) },
        )
    }

    /** Número que tendría la próxima excavación del frente; 1 si el sector o el frente aún no existen. */
    suspend fun siguienteNumero(sector: String, lado: Lado, sentido: Sentido): Int {
        val s = bd.sectores().buscar(sector.trim()) ?: return 1
        val f = bd.frentes().buscar(s.id, lado, sentido) ?: return 1
        return bd.excavaciones().siguienteNumero(f.id)
    }

    /**
     * Crea o actualiza la excavación y reemplaza sus fotos, todo en una transacción. Crea el sector
     * y el frente si no existen. Devuelve el id.
     *
     * @throws ExcavacionInvalida si falta un dato o el número ya lo usa otra excavación del frente.
     */
    suspend fun guardar(borrador: BorradorExcavacion): Long {
        val errores = ValidadorExcavacion.validar(borrador)
        if (errores.isNotEmpty()) throw ExcavacionInvalida(errores)
        val lado = borrador.lado!!
        val sentido = borrador.sentido!!

        return bd.withTransaction {
            val momento = ahora()
            val nombreSector = borrador.sector.trim().replace(Regex("\\s+"), " ")
            val sectorId = bd.sectores().buscar(nombreSector)?.id
                ?: bd.sectores().insertar(SectorEntity(nombre = nombreSector, creadoEn = momento))
            val frenteId = bd.frentes().buscar(sectorId, lado, sentido)?.id
                ?: bd.frentes().insertar(FrenteEntity(sectorId = sectorId, lado = lado, sentido = sentido))

            val anterior = if (borrador.id != 0L) {
                bd.excavaciones().porId(borrador.id) ?: error("La excavación ${borrador.id} ya no existe")
            } else {
                null
            }
            val numero = when {
                borrador.numero != null -> borrador.numero
                // Al editar sin tocar el frente se conserva el número; si cambió de frente, toma el siguiente.
                anterior != null && anterior.frenteId == frenteId -> anterior.numero
                else -> bd.excavaciones().siguienteNumero(frenteId)
            }
            if (bd.excavaciones().ocupante(frenteId, numero, excepto = borrador.id) != null) {
                throw ExcavacionInvalida(mapOf(Campo.NUMERO to "El N° $numero ya está registrado en este sector, lado y sentido"))
            }

            val entidad = ExcavacionEntity(
                id = borrador.id,
                fecha = borrador.fecha,
                frenteId = frenteId,
                numero = numero,
                progresiva = borrador.progresiva?.trim()?.takeIf { it.isNotEmpty() },
                largo = borrador.largo!!,
                ancho = borrador.ancho!!,
                profundidad = borrador.profundidad!!,
                tipoTerrenoId = borrador.tipoTerrenoId,
                latitud = borrador.latitud,
                longitud = borrador.longitud,
                precisionGps = borrador.precisionGps,
                observaciones = borrador.observaciones?.trim()?.takeIf { it.isNotEmpty() },
                creadoEn = anterior?.creadoEn ?: momento,
                actualizadoEn = momento,
            )
            val id = if (anterior == null) {
                bd.excavaciones().insertar(entidad)
            } else {
                bd.excavaciones().actualizar(entidad)
                borrador.id
            }
            bd.fotos().eliminarDe(id)
            bd.fotos().insertar(
                borrador.fotos.mapIndexed { i, f -> FotoEntity(excavacionId = id, orden = i, archivo = f.archivo, tomadaEn = f.tomadaEn) },
            )
            id
        }
    }

    /** Borra la excavación y sus registros de fotos; devuelve los archivos que quedaron sin uso para borrarlos. */
    suspend fun eliminar(id: Long): List<String> = bd.withTransaction {
        val archivos = bd.fotos().de(id).map { it.archivo }
        bd.excavaciones().eliminar(id)
        archivos
    }

    /** Agrega un tipo de terreno al final del menú. Si ya existe (aunque esté desactivado) lo reactiva. */
    suspend fun agregarTipoTerreno(nombre: String): Long = bd.withTransaction {
        val limpio = nombre.trim()
        require(limpio.isNotEmpty()) { "El tipo de terreno no puede quedar vacío" }
        val existente = bd.tiposTerreno().buscar(limpio)?.id
        if (existente != null) {
            bd.tiposTerreno().cambiarActivo(existente, true)
            existente
        } else {
            bd.tiposTerreno().insertar(TipoTerrenoEntity(nombre = limpio, orden = bd.tiposTerreno().siguienteOrden()))
        }
    }

    suspend fun desactivarTipoTerreno(id: Long) = bd.tiposTerreno().cambiarActivo(id, false)

    // ------------------------------------------------------------------ tipos de excavación

    fun tiposExcavacion(): Flow<List<TipoExcavacionEntity>> = bd.tiposExcavacion().observarTodos()

    suspend fun tipoExcavacion(id: Long): TipoExcavacionEntity? = bd.tiposExcavacion().porId(id)

    /**
     * Crea (id 0) o actualiza un tipo de excavación y devuelve su id.
     *
     * @throws IllegalArgumentException si falta el nombre, otro tipo ya lo usa o una medida no es válida.
     */
    suspend fun guardarTipoExcavacion(tipo: TipoExcavacionEntity): Long = bd.withTransaction {
        val nombre = tipo.nombre.trim().replace(Regex("\\s+"), " ")
        require(nombre.isNotEmpty()) { "Ponle un nombre al tipo de excavación" }
        val otro = bd.tiposExcavacion().buscar(nombre)
        require(otro == null || otro.id == tipo.id) { "Ya existe un tipo llamado \"${otro!!.nombre}\"" }
        listOf(tipo.largo, tipo.ancho, tipo.profundidad).forEach { m ->
            require(m == null || (m > 0 && !m.isNaN())) { "Las medidas deben ser mayores que 0 (o dejarse vacías)" }
        }
        val limpio = tipo.copy(nombre = nombre, observaciones = tipo.observaciones?.trim()?.takeIf { it.isNotEmpty() })
        if (tipo.id == 0L) {
            bd.tiposExcavacion().insertar(limpio)
        } else {
            bd.tiposExcavacion().actualizar(limpio)
            tipo.id
        }
    }

    suspend fun eliminarTipoExcavacion(id: Long) = bd.tiposExcavacion().eliminar(id)
}
