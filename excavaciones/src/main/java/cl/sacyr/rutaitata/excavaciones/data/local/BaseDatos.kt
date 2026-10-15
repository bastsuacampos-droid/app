package cl.sacyr.rutaitata.excavaciones.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.sacyr.rutaitata.excavaciones.data.local.dao.ExcavacionDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.FotoDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.FrenteDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.SectorDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.TipoExcavacionDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.TipoTerrenoDao
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FotoEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FrenteEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity

/**
 * Base local; todo lo registrado vive solo aquí (la app funciona sin señal).
 *
 * Para cambiar el esquema: subir [version], agregar una `Migration` a [MIGRACIONES] y su caso en
 * `MigracionesTest`. Nunca `fallbackToDestructiveMigration`: borraría los registros de obra.
 */
@Database(
    entities = [
        SectorEntity::class,
        FrenteEntity::class,
        TipoTerrenoEntity::class,
        ExcavacionEntity::class,
        FotoEntity::class,
        TipoExcavacionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun sectores(): SectorDao
    abstract fun frentes(): FrenteDao
    abstract fun tiposTerreno(): TipoTerrenoDao
    abstract fun excavaciones(): ExcavacionDao
    abstract fun fotos(): FotoDao
    abstract fun tiposExcavacion(): TipoExcavacionDao

    companion object {
        const val NOMBRE = "excavaciones.db"

        /** Catálogo inicial; el usuario puede agregar y desactivar tipos. */
        val TIPOS_TERRENO_INICIALES = listOf(
            "Suelo Tipo I",
            "Suelo Tipo II",
            "Suelo Tipo III",
            "Roca",
            "Asfalto",
            "Hormigón",
            "Base granular",
            "Relleno",
        )

        /** v1 → v2: fecha en que se tomó cada foto (para el anexo fotográfico del reporte). */
        private val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fotos ADD COLUMN tomadaEn INTEGER")
            }
        }

        /** v2 → v3: tipos de excavación preestablecidos (plantillas del formulario). */
        private val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tipos_excavacion` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL COLLATE NOCASE, " +
                        "`largo` REAL, `ancho` REAL, `profundidad` REAL, `tipoTerrenoId` INTEGER, `observaciones` TEXT, " +
                        "FOREIGN KEY(`tipoTerrenoId`) REFERENCES `tipos_terreno`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tipos_excavacion_nombre` ON `tipos_excavacion` (`nombre`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tipos_excavacion_tipoTerrenoId` ON `tipos_excavacion` (`tipoTerrenoId`)")
            }
        }

        internal val MIGRACIONES: Array<Migration> = arrayOf(MIGRACION_1_2, MIGRACION_2_3)

        fun crear(context: Context): BaseDatos =
            Room.databaseBuilder(context, BaseDatos::class.java, NOMBRE)
                .addMigrations(*MIGRACIONES)
                .addCallback(SemillaInicial)
                .build()

        /** Solo para pruebas. */
        fun enMemoria(context: Context): BaseDatos =
            Room.inMemoryDatabaseBuilder(context, BaseDatos::class.java)
                .addCallback(SemillaInicial)
                .allowMainThreadQueries()
                .build()
    }

    private object SemillaInicial : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            TIPOS_TERRENO_INICIALES.forEachIndexed { i, nombre ->
                db.execSQL("INSERT INTO tipos_terreno (nombre, orden, activo) VALUES (?, ?, 1)", arrayOf(nombre, i + 1))
            }
        }
    }
}
