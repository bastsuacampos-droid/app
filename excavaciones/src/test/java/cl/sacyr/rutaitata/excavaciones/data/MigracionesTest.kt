package cl.sacyr.rutaitata.excavaciones.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * Cada migración se prueba sobre una base creada con el esquema exportado de la versión anterior
 * (schemas/…/N.json, el mismo que tienen los teléfonos). Room valida el esquema al abrir: si la
 * migración deja algo distinto de lo que esperan las entidades, la prueba falla.
 */
@RunWith(RobolectricTestRunner::class)
class MigracionesTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun crearVersion(version: Int, archivo: File, datos: SQLiteDatabase.() -> Unit) {
        val esquema = JSONObject(File("schemas/${BaseDatos::class.qualifiedName}/$version.json").readText())
            .getJSONObject("database")
        archivo.delete()
        SQLiteDatabase.openOrCreateDatabase(archivo, null).use { db ->
            val entidades = esquema.getJSONArray("entities")
            for (i in 0 until entidades.length()) {
                val e = entidades.getJSONObject(i)
                val tabla = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", tabla))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tabla))
                }
            }
            val setup = esquema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.datos()
            db.version = version
        }
    }

    @Test
    fun de4a5AgregaElGrupoDeExcavacionesAdyacentes() = runBlocking {
        val archivo = context.getDatabasePath("migracion5.db").apply { parentFile!!.mkdirs() }
        crearVersion(4, archivo) {
            execSQL("INSERT INTO tipos_terreno (id, nombre, orden, activo) VALUES (1, 'Roca', 1, 1)")
            execSQL("INSERT INTO sectores (id, nombre, creadoEn) VALUES (1, 'Sector A', 0)")
            execSQL("INSERT INTO frentes (id, sectorId, lado, sentido) VALUES (1, 1, 'N', 'CH-CO')")
            for (n in 1..2) {
                execSQL(
                    "INSERT INTO excavaciones (id, fecha, frenteId, numero, largo, ancho, profundidad, tipoTerrenoId, creadoEn, actualizadoEn) " +
                        "VALUES ($n, 100, 1, $n, 2.0, 1.0, 1.5, 1, 0, 0)",
                )
            }
            execSQL("INSERT INTO cierres_dia (fecha, cerradoEn, responsable) VALUES (99, 0, 'Jefe')")
        }
        val bd = Room.databaseBuilder(context, BaseDatos::class.java, archivo.absolutePath)
            .addMigrations(*BaseDatos.MIGRACIONES)
            .build()
        try {
            val repo = RepositorioExcavaciones(bd)
            val dia = LocalDate.ofEpochDay(100)
            assertTrue(repo.paraReporte(dia, dia, null).all { it.grupo == null })
            assertEquals("Jefe", repo.cierre(LocalDate.ofEpochDay(99)).first()!!.responsable)
            repo.agrupar(listOf(1L, 2L))
            assertEquals(1, repo.paraReporte(dia, dia, null).map { it.grupo }.distinct().filterNotNull().size)
        } finally {
            bd.close()
        }
    }

    @Test
    fun de3a4AgregaOrigenDeFotosYCierresDeDia() = runBlocking {
        val archivo = context.getDatabasePath("migracion4.db").apply { parentFile!!.mkdirs() }
        crearVersion(3, archivo) {
            execSQL("INSERT INTO tipos_terreno (id, nombre, orden, activo) VALUES (1, 'Roca', 1, 1)")
            execSQL("INSERT INTO sectores (id, nombre, creadoEn) VALUES (1, 'Sector A', 0)")
            execSQL("INSERT INTO frentes (id, sectorId, lado, sentido) VALUES (1, 1, 'N', 'CH-CO')")
            execSQL(
                "INSERT INTO excavaciones (id, fecha, frenteId, numero, largo, ancho, profundidad, tipoTerrenoId, creadoEn, actualizadoEn) " +
                    "VALUES (1, 100, 1, 1, 2.0, 1.0, 1.5, 1, 0, 0)",
            )
            execSQL("INSERT INTO fotos (excavacionId, orden, archivo, tomadaEn) VALUES (1, 0, 'f.jpg', 5)")
            execSQL("INSERT INTO tipos_excavacion (nombre, largo, ancho, profundidad) VALUES ('Pocillo', 1.0, 1.0, 1.5)")
        }
        val bd = Room.databaseBuilder(context, BaseDatos::class.java, archivo.absolutePath)
            .addMigrations(*BaseDatos.MIGRACIONES)
            .build()
        try {
            val repo = RepositorioExcavaciones(bd)
            val foto = repo.borrador(1)!!.fotos.single()
            assertNull(foto.origen)
            assertEquals(5L, foto.tomadaEn)
            assertEquals("Pocillo", repo.tiposExcavacion().first().single().nombre)
            val dia = LocalDate.ofEpochDay(100)
            assertEquals(1, repo.cerrarDias(dia, dia, "Jefe"))
            assertEquals("Jefe", repo.cierre(dia).first()!!.responsable)
        } finally {
            bd.close()
        }
    }

    @Test
    fun de2a3AgregaLosTiposDeExcavacionSinTocarLoDemas() = runBlocking {
        val archivo = context.getDatabasePath("migracion3.db").apply { parentFile!!.mkdirs() }
        crearVersion(2, archivo) {
            execSQL("INSERT INTO tipos_terreno (id, nombre, orden, activo) VALUES (1, 'Roca', 1, 1)")
            execSQL("INSERT INTO sectores (id, nombre, creadoEn) VALUES (1, 'Sector A', 0)")
            execSQL("INSERT INTO frentes (id, sectorId, lado, sentido) VALUES (1, 1, 'S', 'CO-CH')")
            execSQL(
                "INSERT INTO excavaciones (id, fecha, frenteId, numero, largo, ancho, profundidad, tipoTerrenoId, creadoEn, actualizadoEn) " +
                    "VALUES (1, 0, 1, 3, 2.0, 1.0, 1.5, 1, 0, 0)",
            )
            execSQL("INSERT INTO fotos (excavacionId, orden, archivo, tomadaEn) VALUES (1, 0, 'f.jpg', 99)")
        }
        val bd = Room.databaseBuilder(context, BaseDatos::class.java, archivo.absolutePath)
            .addMigrations(*BaseDatos.MIGRACIONES)
            .build()
        try {
            val repo = RepositorioExcavaciones(bd)
            assertEquals(99L, repo.borrador(1)!!.fotos.single().tomadaEn)
            assertEquals(emptyList<Any>(), repo.tiposExcavacion().first())
            val id = repo.guardarTipoExcavacion(TipoExcavacionEntity(nombre = "Pocillo", largo = 1.0, ancho = 1.0, profundidad = 1.5, tipoTerrenoId = 1, observaciones = null))
            assertEquals("Pocillo", repo.tipoExcavacion(id)!!.nombre)
        } finally {
            bd.close()
        }
    }

    @Test
    fun de1a2ConservaLosRegistrosYLasFotosQuedanSinFecha() = runBlocking {
        val archivo = context.getDatabasePath("migracion.db").apply { parentFile!!.mkdirs() }
        crearVersion(1, archivo) {
            execSQL("INSERT INTO tipos_terreno (id, nombre, orden, activo) VALUES (1, 'Roca', 1, 1)")
            execSQL("INSERT INTO sectores (id, nombre, creadoEn) VALUES (1, 'Sector A', 0)")
            execSQL("INSERT INTO frentes (id, sectorId, lado, sentido) VALUES (1, 1, 'N', 'CH-CO')")
            execSQL(
                "INSERT INTO excavaciones (id, fecha, frenteId, numero, largo, ancho, profundidad, tipoTerrenoId, creadoEn, actualizadoEn) " +
                    "VALUES (1, ${LocalDate.of(2026, 10, 15).toEpochDay()}, 1, 7, 2.0, 1.0, 1.5, 1, 0, 0)",
            )
            execSQL("INSERT INTO fotos (excavacionId, orden, archivo) VALUES (1, 0, 'antigua.jpg')")
        }

        val bd = Room.databaseBuilder(context, BaseDatos::class.java, archivo.absolutePath)
            .addMigrations(*BaseDatos.MIGRACIONES)
            .build()
        try {
            val repo = RepositorioExcavaciones(bd)
            val e = repo.borrador(1)!!
            assertEquals(7, e.numero)
            assertEquals("Sector A", e.sector)
            assertEquals("antigua.jpg", e.fotos.single().archivo)
            assertNull(e.fotos.single().tomadaEn)
        } finally {
            bd.close()
        }
    }
}
