package cl.sacyr.rutaitata.excavaciones.data

import androidx.room.withTransaction
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.data.local.dao.SectorConConteo
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.CierreDiaEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ReaperturaEntity
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
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoSector
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import cl.sacyr.rutaitata.excavaciones.dominio.ValidadorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Volumen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Otro sector ya tiene el nombre pedido. */
class SectorExistente(val otro: SectorEntity) : IllegalArgumentException("Ya existe el sector \"${otro.nombre}\"")

/** Una excavación que cambió de número al unir sectores. */
data class Renumeracion(val lado: Lado, val sentido: Sentido, val antes: Int, val despues: Int)

/** Se intentó borrar una excavación de un día cerrado. */
class DiaCerrado(mensaje: String) : IllegalStateException(mensaje)

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
            fotos = bd.fotos().de(id).map { FotoBorrador(it.archivo, it.tomadaEn, OrigenFoto.desdeCodigo(it.origen)) },
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
            // Ni agregar a un día cerrado, ni sacar o cambiar una excavación de uno.
            for (fecha in setOfNotNull(borrador.fecha, anterior?.fecha)) {
                if (bd.cierres().de(fecha) != null) throw ExcavacionInvalida(mapOf(Campo.FECHA to mensajeCerrado(fecha)))
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
                borrador.fotos.mapIndexed { i, f ->
                    FotoEntity(excavacionId = id, orden = i, archivo = f.archivo, tomadaEn = f.tomadaEn, origen = f.origen?.codigo)
                },
            )
            id
        }
    }

    /** Borra la excavación y sus registros de fotos; devuelve los archivos que quedaron sin uso para borrarlos. */
    suspend fun eliminar(id: Long): List<String> = bd.withTransaction {
        bd.excavaciones().porId(id)?.let { e ->
            if (bd.cierres().de(e.fecha) != null) throw DiaCerrado(mensajeCerrado(e.fecha))
        }
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

    // ------------------------------------------------------------------ sectores

    fun sectoresConConteo(): Flow<List<SectorConConteo>> = bd.sectores().observarConConteo()

    /**
     * Cambia el nombre de un sector (para corregir cómo se escribió).
     *
     * @throws SectorExistente si otro sector ya se llama así: entonces corresponde unirlos.
     * @throws DiaCerrado si tiene excavaciones en días cerrados (su reporte ya se emitió).
     */
    suspend fun renombrarSector(id: Long, nombre: String) = bd.withTransaction {
        val limpio = nombre.trim().replace(Regex("\\s+"), " ")
        require(limpio.isNotEmpty()) { "El nombre del sector no puede quedar vacío" }
        bd.sectores().buscar(limpio)?.let { otro -> if (otro.id != id) throw SectorExistente(otro) }
        exigirSinDiasCerrados(id)
        bd.sectores().renombrar(id, limpio)
    }

    /**
     * Une el sector [origenId] con [destinoId] (el que quedará): sus excavaciones pasan al destino
     * con su lado y sentido. Si un número ya está usado en ese frente del destino, la excavación
     * que se mueve toma el siguiente libre. Devuelve los cambios de número para avisarlos.
     */
    suspend fun unirSectores(origenId: Long, destinoId: Long): List<Renumeracion> = bd.withTransaction {
        require(origenId != destinoId) { "Elige otro sector" }
        exigirSinDiasCerrados(origenId)
        val momento = ahora()
        val cambios = mutableListOf<Renumeracion>()
        for (frente in bd.frentes().deSector(origenId)) {
            val destino = bd.frentes().buscar(destinoId, frente.lado, frente.sentido)
            if (destino == null) {
                bd.frentes().mover(frente.id, destinoId) // el destino no tenía ese lado/sentido: pasa entero
                continue
            }
            val delDestino = bd.excavaciones().delFrente(destino.id).map { it.numero }.toSet()
            val (chocan, libres) = bd.excavaciones().delFrente(frente.id).partition { it.numero in delDestino }
            // Primero las que conservan su número; las que chocan van después del mayor.
            val usados = (delDestino + libres.map { it.numero }).toMutableSet()
            libres.forEach { bd.excavaciones().mover(it.id, destino.id, it.numero, momento) }
            for (e in chocan) {
                val numero = usados.max() + 1
                usados += numero
                cambios += Renumeracion(frente.lado, frente.sentido, e.numero, numero)
                bd.excavaciones().mover(e.id, destino.id, numero, momento)
            }
            bd.frentes().eliminar(frente.id)
        }
        bd.sectores().eliminar(origenId)
        cambios
    }

    /** Borra un sector sin excavaciones (queda, por ejemplo, después de borrar las suyas). */
    suspend fun eliminarSector(id: Long) = bd.withTransaction {
        bd.frentes().deSector(id).forEach { f ->
            require(bd.excavaciones().delFrente(f.id).isEmpty()) { "El sector tiene excavaciones: únelo con otro en vez de borrarlo" }
            bd.frentes().eliminar(f.id)
        }
        bd.sectores().eliminar(id)
    }

    private suspend fun exigirSinDiasCerrados(sectorId: Long) {
        val dias = bd.sectores().diasCerrados(sectorId)
        if (dias.isNotEmpty()) {
            throw DiaCerrado(
                "Tiene excavaciones en días cerrados (${dias.joinToString(", ") { Formato.fecha(it) }}). Reábrelos primero.",
            )
        }
    }

    // ------------------------------------------------------------------ cierre de días

    fun cierre(fecha: LocalDate): Flow<CierreDiaEntity?> = bd.cierres().observar(fecha)

    fun cierresEntre(desde: LocalDate, hasta: LocalDate): Flow<List<CierreDiaEntity>> =
        bd.cierres().observarEntre(minOf(desde, hasta), maxOf(desde, hasta))

    /**
     * Cierra los días del período (al emitir el reporte definitivo): sus excavaciones quedan
     * fijas. Los días ya cerrados conservan su cierre original. Devuelve cuántos se cerraron.
     */
    suspend fun cerrarDias(desde: LocalDate, hasta: LocalDate, responsable: String): Int {
        val momento = ahora()
        val quien = responsable.trim().ifEmpty { "Sin indicar" }
        val dias = generateSequence(minOf(desde, hasta)) { it.plusDays(1) }.takeWhile { !it.isAfter(maxOf(desde, hasta)) }
        return bd.cierres().cerrar(dias.map { CierreDiaEntity(it, momento, quien) }.toList()).count { it != -1L }
    }

    /** Reabre un día cerrado para corregirlo; el motivo queda registrado y sale en sus reportes. */
    suspend fun reabrirDia(fecha: LocalDate, motivo: String) = bd.withTransaction {
        require(motivo.isNotBlank()) { "Escribe el motivo para reabrir el día" }
        val cierre = bd.cierres().de(fecha) ?: return@withTransaction
        bd.cierres().borrar(fecha)
        bd.cierres().insertarReapertura(
            ReaperturaEntity(
                fecha = fecha,
                cerradoEn = cierre.cerradoEn,
                responsableCierre = cierre.responsable,
                reabiertoEn = ahora(),
                motivo = motivo.trim(),
            ),
        )
    }

    suspend fun cierresYReaperturas(desde: LocalDate, hasta: LocalDate): Pair<List<CierreDiaEntity>, List<ReaperturaEntity>> =
        bd.cierres().entre(desde, hasta) to bd.cierres().reaperturasEntre(desde, hasta)

    private fun mensajeCerrado(fecha: LocalDate) =
        "El día ${Formato.fecha(fecha)} está cerrado (reporte emitido). Reábrelo desde el dashboard para modificarlo."

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
