package cl.sacyr.rutaitata.excavaciones.data.local.entidades

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import java.time.LocalDate

/*
 * Jerarquía en la base:  Sector ─< Frente (lado + sentido) ─< Excavación ─< Foto
 *                                                                └─> Tipo de terreno
 *
 * El frente existe para que la numeración correlativa tenga dónde vivir: el número de una
 * excavación es único dentro de su frente (índice único frenteId + numero), y "Sector A, Lado Norte,
 * Chillán → Concepción" es una sola fila que se crea al registrar la primera excavación.
 *
 * Borrados: sectores, frentes y tipos de terreno con excavaciones no se pueden borrar (RESTRICT);
 * los tipos se desactivan. Las fotos se van con su excavación (CASCADE).
 */

/** Nivel 1: sector o tramo, como lo escribe el usuario ("Sector A", "Km 12+400 a 14+200"). */
@Entity(
    tableName = "sectores",
    indices = [Index(value = ["nombre"], unique = true)],
)
data class SectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Único sin distinguir mayúsculas: "sector a" y "Sector A" son el mismo. */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val nombre: String,
    val creadoEn: Long,
)

/** Niveles 2 y 3: calzada y sentido de avance dentro de un sector. */
@Entity(
    tableName = "frentes",
    foreignKeys = [
        ForeignKey(
            entity = SectorEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectorId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["sectorId", "lado", "sentido"], unique = true)],
)
data class FrenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectorId: Long,
    val lado: Lado,
    val sentido: Sentido,
)

/** Catálogo editable de materiales o terrenos (Suelo Tipo I, Roca, Asfalto…). */
@Entity(
    tableName = "tipos_terreno",
    indices = [Index(value = ["nombre"], unique = true)],
)
data class TipoTerrenoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val nombre: String,
    /** Posición en el menú desplegable. */
    val orden: Int,
    /** Los desactivados no se ofrecen al registrar, pero siguen en los registros antiguos. */
    @ColumnInfo(defaultValue = "1") val activo: Boolean = true,
)

/**
 * Tipo de excavación preestablecido (plantilla): al elegirlo en el formulario llena las medidas,
 * el terreno y las observaciones. Una medida null queda para escribirla en cada excavación (el
 * largo de una zanja, por ejemplo). No se guarda en la excavación: solo ahorra escribir.
 */
@Entity(
    tableName = "tipos_excavacion",
    foreignKeys = [
        ForeignKey(
            entity = TipoTerrenoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tipoTerrenoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["nombre"], unique = true), Index(value = ["tipoTerrenoId"])],
)
data class TipoExcavacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val nombre: String,
    val largo: Double?,
    val ancho: Double?,
    val profundidad: Double?,
    val tipoTerrenoId: Long?,
    val observaciones: String?,
)

/** Nivel 4: una excavación ejecutada en terreno. */
@Entity(
    tableName = "excavaciones",
    foreignKeys = [
        ForeignKey(
            entity = FrenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["frenteId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TipoTerrenoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tipoTerrenoId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["frenteId", "numero"], unique = true),
        Index(value = ["fecha"]),
        Index(value = ["tipoTerrenoId"]),
    ],
)
data class ExcavacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Día de la jornada (se guarda como días desde 1970-01-01, ver Conversores). */
    val fecha: LocalDate,
    val frenteId: Long,
    /** Correlativo dentro del frente, en el sentido de avance: 1, 2, 3… */
    val numero: Int,
    val progresiva: String?,
    /** Metros. El volumen no se guarda: se calcula (largo × ancho × profundidad). */
    val largo: Double,
    val ancho: Double,
    val profundidad: Double,
    val tipoTerrenoId: Long?,
    val latitud: Double?,
    val longitud: Double?,
    /** Precisión informada por el GPS al capturar, en metros. */
    val precisionGps: Float?,
    val observaciones: String?,
    val creadoEn: Long,
    val actualizadoEn: Long,
    /**
     * Excavaciones adyacentes del mismo día y frente que en el reporte van en una sola fila:
     * comparten este valor (el id de la primera que se agrupó). null: no está agrupada.
     */
    val grupo: Long? = null,
)

/** Hasta 3 fotos por excavación; el archivo vive en files/fotos/[archivo]. */
@Entity(
    tableName = "fotos",
    foreignKeys = [
        ForeignKey(
            entity = ExcavacionEntity::class,
            parentColumns = ["id"],
            childColumns = ["excavacionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["excavacionId", "orden"], unique = true)],
)
data class FotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val excavacionId: Long,
    /** 0, 1 o 2. */
    val orden: Int,
    val archivo: String,
    /** Cuándo se tomó (epoch ms): EXIF de la foto o el momento de la captura. null si no se sabe. Desde la versión 2. */
    val tomadaEn: Long? = null,
    /** Código de [OrigenFoto] ("C" cámara, "G" galería); null en fotos anteriores a la versión 4. */
    val origen: String? = null,
)

/**
 * Día cerrado: se emitió el reporte definitivo y sus excavaciones ya no se pueden agregar, editar
 * ni borrar. Reabrirlo borra esta fila y deja constancia en [ReaperturaEntity].
 */
@Entity(tableName = "cierres_dia")
data class CierreDiaEntity(
    @PrimaryKey val fecha: LocalDate,
    val cerradoEn: Long,
    val responsable: String,
)

/** Constancia de que un día cerrado se reabrió, y por qué. Sale en los reportes de ese día. */
@Entity(tableName = "reaperturas", indices = [Index(value = ["fecha"])])
data class ReaperturaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: LocalDate,
    /** Del cierre que se deshizo. */
    val cerradoEn: Long,
    val responsableCierre: String,
    val reabiertoEn: Long,
    val motivo: String,
)
