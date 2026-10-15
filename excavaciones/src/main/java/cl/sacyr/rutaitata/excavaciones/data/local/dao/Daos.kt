package cl.sacyr.rutaitata.excavaciones.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FotoEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FrenteEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
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

    @Insert
    suspend fun insertar(sector: SectorEntity): Long
}

@Dao
interface FrenteDao {
    @Query("SELECT * FROM frentes WHERE sectorId = :sectorId AND lado = :lado AND sentido = :sentido LIMIT 1")
    suspend fun buscar(sectorId: Long, lado: Lado, sentido: Sentido): FrenteEntity?

    @Insert
    suspend fun insertar(frente: FrenteEntity): Long
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

    companion object {
        /** Columnas con los nombres de las propiedades de [ExcavacionDetalle]. */
        const val SELECT_DETALLE = """
            SELECT e.id, e.fecha, s.id AS sectorId, s.nombre AS sector, f.lado, f.sentido, e.numero,
                   e.progresiva, e.largo, e.ancho, e.profundidad, t.nombre AS tipoTerreno,
                   e.latitud, e.longitud, e.observaciones,
                   (SELECT COUNT(*) FROM fotos ft WHERE ft.excavacionId = e.id) AS fotos
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

    @Query("DELETE FROM fotos WHERE excavacionId = :excavacionId")
    suspend fun eliminarDe(excavacionId: Long)

    @Insert
    suspend fun insertar(fotos: List<FotoEntity>)
}
