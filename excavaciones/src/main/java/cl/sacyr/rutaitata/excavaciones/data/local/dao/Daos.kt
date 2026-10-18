package cl.sacyr.rutaitata.excavaciones.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.CierreDiaEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ReaperturaEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FotoEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FrenteEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SectorDao {
    @Query("SELECT * FROM sectores ORDER BY nombre")
    fun observarTodos(): Flow<List<SectorEntity>>

    @Query("SELECT * FROM sectores WHERE nombre = :nombre LIMIT 1")
    suspend fun buscar(nombre: String): SectorEntity?

    @Query("SELECT * FROM sectores WHERE id = :id")
    suspend fun porId(id: Long): SectorEntity?

    @Insert
    suspend fun insertar(sector: SectorEntity): Long

    /** Sectores con cuántas excavaciones tienen, para administrarlos. */
    @Query(
        """SELECT s.id, s.nombre, COUNT(e.id) AS excavaciones FROM sectores s
           LEFT JOIN frentes f ON f.sectorId = s.id LEFT JOIN excavaciones e ON e.frenteId = f.id
           GROUP BY s.id ORDER BY s.nombre""",
    )
    fun observarConConteo(): Flow<List<SectorConConteo>>

    @Query("UPDATE sectores SET nombre = :nombre WHERE id = :id")
    suspend fun renombrar(id: Long, nombre: String)

    @Query("DELETE FROM sectores WHERE id = :id")
    suspend fun eliminar(id: Long)

    /** Días cerrados en que el sector tiene excavaciones. */
    @Query(
        """SELECT DISTINCT e.fecha FROM excavaciones e JOIN frentes f ON f.id = e.frenteId
           JOIN cierres_dia c ON c.fecha = e.fecha WHERE f.sectorId = :sectorId ORDER BY e.fecha""",
    )
    suspend fun diasCerrados(sectorId: Long): List<LocalDate>
}

data class SectorConConteo(val id: Long, val nombre: String, val excavaciones: Int)

@Dao
interface FrenteDao {
    @Query("SELECT * FROM frentes WHERE sectorId = :sectorId AND lado = :lado AND sentido = :sentido LIMIT 1")
    suspend fun buscar(sectorId: Long, lado: Lado, sentido: Sentido): FrenteEntity?

    @Query("SELECT * FROM frentes WHERE id = :id")
    suspend fun porId(id: Long): FrenteEntity?

    @Insert
    suspend fun insertar(frente: FrenteEntity): Long

    @Query("SELECT * FROM frentes WHERE sectorId = :sectorId")
    suspend fun deSector(sectorId: Long): List<FrenteEntity>

    @Query("UPDATE frentes SET sectorId = :sectorId WHERE id = :id")
    suspend fun mover(id: Long, sectorId: Long)

    @Query("DELETE FROM frentes WHERE id = :id")
    suspend fun eliminar(id: Long)
}

@Dao
interface TipoTerrenoDao {
    @Query("SELECT * FROM tipos_terreno WHERE activo = 1 ORDER BY orden, nombre")
    fun observarActivos(): Flow<List<TipoTerrenoEntity>>

    /** Incluye los desactivados; el nombre se compara sin distinguir mayúsculas (columna NOCASE). */
    @Query("SELECT * FROM tipos_terreno WHERE nombre = :nombre LIMIT 1")
    suspend fun buscar(nombre: String): TipoTerrenoEntity?

    @Query("SELECT COALESCE(MAX(orden), 0) + 1 FROM tipos_terreno")
    suspend fun siguienteOrden(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(tipo: TipoTerrenoEntity): Long

    @Query("UPDATE tipos_terreno SET activo = :activo WHERE id = :id")
    suspend fun cambiarActivo(id: Long, activo: Boolean)
}

@Dao
interface TipoExcavacionDao {
    @Query("SELECT * FROM tipos_excavacion ORDER BY nombre")
    fun observarTodos(): Flow<List<TipoExcavacionEntity>>

    @Query("SELECT * FROM tipos_excavacion WHERE id = :id")
    suspend fun porId(id: Long): TipoExcavacionEntity?

    /** Sin distinguir mayúsculas (columna NOCASE). */
    @Query("SELECT * FROM tipos_excavacion WHERE nombre = :nombre LIMIT 1")
    suspend fun buscar(nombre: String): TipoExcavacionEntity?

    @Insert
    suspend fun insertar(tipo: TipoExcavacionEntity): Long

    @Update
    suspend fun actualizar(tipo: TipoExcavacionEntity)

    @Query("DELETE FROM tipos_excavacion WHERE id = :id")
    suspend fun eliminar(id: Long)
}

@Dao
interface ExcavacionDao {
    @Query("$SELECT_DETALLE WHERE e.fecha = :fecha $ORDEN")
    fun observarDelDia(fecha: LocalDate): Flow<List<ExcavacionDetalle>>

    /** Excavaciones entre dos fechas (incluidas), de un sector o de todos ([sectorId] null). */
    @Query("$SELECT_DETALLE WHERE e.fecha BETWEEN :desde AND :hasta AND (:sectorId IS NULL OR s.id = :sectorId) $ORDEN")
    suspend fun entre(desde: LocalDate, hasta: LocalDate, sectorId: Long?): List<ExcavacionDetalle>

    @Query("SELECT * FROM excavaciones WHERE id = :id")
    suspend fun porId(id: Long): ExcavacionEntity?

    @Query("SELECT COALESCE(MAX(numero), 0) + 1 FROM excavaciones WHERE frenteId = :frenteId")
    suspend fun siguienteNumero(frenteId: Long): Int

    /** Id de otra excavación del frente que ya usa [numero], o null si está libre. */
    @Query("SELECT id FROM excavaciones WHERE frenteId = :frenteId AND numero = :numero AND id != :excepto LIMIT 1")
    suspend fun ocupante(frenteId: Long, numero: Int, excepto: Long): Long?

    @Insert
    suspend fun insertar(excavacion: ExcavacionEntity): Long

    @Update
    suspend fun actualizar(excavacion: ExcavacionEntity)

    @Query("DELETE FROM excavaciones WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Query("SELECT * FROM excavaciones WHERE id IN (:ids)")
    suspend fun porIds(ids: List<Long>): List<ExcavacionEntity>

    @Query("SELECT * FROM excavaciones WHERE grupo = :grupo")
    suspend fun delGrupo(grupo: Long): List<ExcavacionEntity>

    /** Clave libre para un grupo nuevo de excavaciones adyacentes. */
    @Query("SELECT COALESCE(MAX(grupo), 0) + 1 FROM excavaciones")
    suspend fun nuevoGrupo(): Long

    @Query("UPDATE excavaciones SET grupo = :grupo, actualizadoEn = :momento WHERE id IN (:ids)")
    suspend fun ponerGrupo(ids: List<Long>, grupo: Long?, momento: Long)

    @Query("SELECT * FROM excavaciones WHERE frenteId = :frenteId ORDER BY numero")
    suspend fun delFrente(frenteId: Long): List<ExcavacionEntity>

    @Query("UPDATE excavaciones SET frenteId = :frenteId, numero = :numero, actualizadoEn = :momento WHERE id = :id")
    suspend fun mover(id: Long, frenteId: Long, numero: Int, momento: Long)

    companion object {
        /** Columnas con los nombres de las propiedades de [ExcavacionDetalle]. */
        const val SELECT_DETALLE = """
            SELECT e.id, e.fecha, s.id AS sectorId, s.nombre AS sector, f.lado, f.sentido, e.numero,
                   e.progresiva, e.largo, e.ancho, e.profundidad, t.nombre AS tipoTerreno,
                   e.latitud, e.longitud, e.observaciones,
                   (SELECT COUNT(*) FROM fotos ft WHERE ft.excavacionId = e.id) AS fotos, e.grupo
            FROM excavaciones e
            JOIN frentes f ON f.id = e.frenteId
            JOIN sectores s ON s.id = f.sectorId
            LEFT JOIN tipos_terreno t ON t.id = e.tipoTerrenoId
        """
        const val ORDEN = "ORDER BY s.nombre, f.lado, f.sentido, e.numero"
    }
}

@Dao
interface FotoDao {
    @Query("SELECT * FROM fotos WHERE excavacionId = :excavacionId ORDER BY orden")
    suspend fun de(excavacionId: Long): List<FotoEntity>

    @Query("SELECT * FROM fotos WHERE excavacionId IN (:excavaciones)")
    suspend fun deVarias(excavaciones: List<Long>): List<FotoEntity>

    @Query("SELECT archivo FROM fotos")
    suspend fun archivos(): List<String>

    @Query("DELETE FROM fotos WHERE excavacionId = :excavacionId")
    suspend fun eliminarDe(excavacionId: Long)

    @Insert
    suspend fun insertar(fotos: List<FotoEntity>)
}

@Dao
interface CierreDao {
    @Query("SELECT * FROM cierres_dia WHERE fecha = :fecha")
    fun observar(fecha: LocalDate): Flow<CierreDiaEntity?>

    @Query("SELECT * FROM cierres_dia WHERE fecha = :fecha")
    suspend fun de(fecha: LocalDate): CierreDiaEntity?

    @Query("SELECT * FROM cierres_dia WHERE fecha BETWEEN :desde AND :hasta ORDER BY fecha")
    fun observarEntre(desde: LocalDate, hasta: LocalDate): Flow<List<CierreDiaEntity>>

    @Query("SELECT * FROM cierres_dia WHERE fecha BETWEEN :desde AND :hasta ORDER BY fecha")
    suspend fun entre(desde: LocalDate, hasta: LocalDate): List<CierreDiaEntity>

    /** Un día ya cerrado conserva su cierre original. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun cerrar(cierres: List<CierreDiaEntity>): List<Long>

    @Query("DELETE FROM cierres_dia WHERE fecha = :fecha")
    suspend fun borrar(fecha: LocalDate)

    @Insert
    suspend fun insertarReapertura(reapertura: ReaperturaEntity): Long

    @Query("SELECT * FROM reaperturas WHERE fecha BETWEEN :desde AND :hasta ORDER BY reabiertoEn")
    suspend fun reaperturasEntre(desde: LocalDate, hasta: LocalDate): List<ReaperturaEntity>
}

/** Lectura y reemplazo de todo el contenido, para el respaldo completo (ver Respaldo). */
@Dao
interface RespaldoDao {
    @Query("SELECT * FROM tipos_terreno ORDER BY id") suspend fun tiposTerreno(): List<TipoTerrenoEntity>
    @Query("SELECT * FROM sectores ORDER BY id") suspend fun sectores(): List<SectorEntity>
    @Query("SELECT * FROM frentes ORDER BY id") suspend fun frentes(): List<FrenteEntity>
    @Query("SELECT * FROM tipos_excavacion ORDER BY id") suspend fun tiposExcavacion(): List<TipoExcavacionEntity>
    @Query("SELECT * FROM excavaciones ORDER BY id") suspend fun excavaciones(): List<ExcavacionEntity>
    @Query("SELECT * FROM fotos ORDER BY id") suspend fun fotos(): List<FotoEntity>
    @Query("SELECT * FROM cierres_dia ORDER BY fecha") suspend fun cierres(): List<CierreDiaEntity>
    @Query("SELECT * FROM reaperturas ORDER BY id") suspend fun reaperturas(): List<ReaperturaEntity>

    @Query("SELECT COUNT(*) FROM excavaciones") fun observarCantidad(): Flow<Int>

    @Query("DELETE FROM reaperturas") suspend fun borrarReaperturas()
    @Query("DELETE FROM cierres_dia") suspend fun borrarCierres()
    @Query("DELETE FROM fotos") suspend fun borrarFotos()
    @Query("DELETE FROM excavaciones") suspend fun borrarExcavaciones()
    @Query("DELETE FROM tipos_excavacion") suspend fun borrarTiposExcavacion()
    @Query("DELETE FROM frentes") suspend fun borrarFrentes()
    @Query("DELETE FROM sectores") suspend fun borrarSectores()
    @Query("DELETE FROM tipos_terreno") suspend fun borrarTiposTerreno()

    @Insert suspend fun insertarTiposTerreno(l: List<TipoTerrenoEntity>)
    @Insert suspend fun insertarSectores(l: List<SectorEntity>)
    @Insert suspend fun insertarFrentes(l: List<FrenteEntity>)
    @Insert suspend fun insertarTiposExcavacion(l: List<TipoExcavacionEntity>)
    @Insert suspend fun insertarExcavaciones(l: List<ExcavacionEntity>)
    @Insert suspend fun insertarFotos(l: List<FotoEntity>)
    @Insert suspend fun insertarCierres(l: List<CierreDiaEntity>)
    @Insert suspend fun insertarReaperturas(l: List<ReaperturaEntity>)
}
