package cl.sacyr.rutaitata.excavaciones.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.sacyr.rutaitata.excavaciones.data.local.dao.ExcavacionDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.FotoDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.FrenteDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.SectorDao
import cl.sacyr.rutaitata.excavaciones.data.local.dao.TipoTerrenoDao
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.ExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FotoEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.FrenteEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity

/**
 * Base local; todo lo registrado vive solo aquí (la app funciona sin señal).
 *
 * Para cambiar el esquema: subir [version], agregar una `Migration` a [MIGRACIONES] y una prueba
 * con `MigrationTestHelper`. Nunca `fallbackToDestructiveMigration`: borraría los registros de obra.
 */
@Database(
    entities = [
        SectorEntity::class,
        FrenteEntity::class,
        TipoTerrenoEntity::class,
        ExcavacionEntity::class,
        FotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun sectores(): SectorDao
    abstract fun frentes(): FrenteDao
    abstract fun tiposTerreno(): TipoTerrenoDao
    abstract fun excavaciones(): ExcavacionDao
    abstract fun fotos(): FotoDao

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

        private val MIGRACIONES = emptyArray<androidx.room.migration.Migration>()

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
