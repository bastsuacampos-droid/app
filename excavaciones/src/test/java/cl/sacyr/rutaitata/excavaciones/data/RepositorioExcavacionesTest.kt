package cl.sacyr.rutaitata.excavaciones.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Campo
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionInvalida
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RepositorioExcavacionesTest {

    private lateinit var bd: BaseDatos
    private lateinit var repo: RepositorioExcavaciones
    private val hoy = LocalDate.of(2026, 10, 15)
    private var suelo = 0L

    @Before
    fun abrir() = runBlocking {
        bd = BaseDatos.enMemoria(ApplicationProvider.getApplicationContext())
        repo = RepositorioExcavaciones(bd) { 1_000L }
        suelo = repo.tiposTerreno().first().first().id
    }

    @After
    fun cerrar() = bd.close()

    private fun borrador(
        sector: String = "Sector A",
        lado: Lado = Lado.NORTE,
        sentido: Sentido = Sentido.CHILLAN_CONCEPCION,
        numero: Int? = null,
        fecha: LocalDate = hoy,
    ) = BorradorExcavacion(
        fecha = fecha, sector = sector, lado = lado, sentido = sentido, numero = numero,
        largo = 4.0, ancho = 1.2, profundidad = 1.5, tipoTerrenoId = suelo,
    )

    private fun fotos(vararg archivos: String) = archivos.mapIndexed { i, a -> FotoBorrador(a, tomadaEn = 1_000L + i) }

    @Test
    fun elBorradorGuardadoSeRecuperaIgual() = runBlocking {
        val original = borrador(numero = 9).copy(
            progresiva = "Km 12+450", latitud = -36.6, longitud = -72.1, precisionGps = 4.5f,
            observaciones = "Sin interferencias", fotos = fotos("a.jpg", "b.jpg"),
        )
        val id = repo.guardar(original)
        assertEquals(original.copy(id = id), repo.borrador(id))
        assertNull(repo.borrador(id + 100))
    }

    @Test
    fun fotosParaElReporteAgrupadasYEnOrden() = runBlocking {
        val a = repo.guardar(borrador().copy(fotos = fotos("a1.jpg", "a2.jpg")))
        val b = repo.guardar(borrador().copy(fotos = fotos("b1.jpg")))
        repo.guardar(borrador())
        val fotos = repo.fotosDe(listOf(a, b, a))
        assertEquals(listOf("a1.jpg", "a2.jpg"), fotos[a]!!.map { it.archivo })
        assertEquals(1_001L, fotos[a]!![1].tomadaEn)
        assertEquals(listOf("b1.jpg"), fotos[b]!!.map { it.archivo })
        assertEquals(setOf("a1.jpg", "a2.jpg", "b1.jpg"), repo.archivosDeFotosEnUso())
    }

    private fun tipo(nombre: String, largo: Double? = 1.0) =
        TipoExcavacionEntity(nombre = nombre, largo = largo, ancho = 1.0, profundidad = 1.5, tipoTerrenoId = suelo, observaciones = "  Con entibado ")

    @Test
    fun tiposDeExcavacionSeCreanEditanYBorran() = runBlocking {
        val id = repo.guardarTipoExcavacion(tipo("  Pocillo   de inspección "))
        val guardado = repo.tipoExcavacion(id)!!
        assertEquals("Pocillo de inspección", guardado.nombre)
        assertEquals("Con entibado", guardado.observaciones)

        // Una zanja: el largo queda para escribirlo cada vez.
        val zanja = repo.guardarTipoExcavacion(tipo("Zanja 0,60", largo = null))
        assertNull(repo.tipoExcavacion(zanja)!!.largo)
        assertEquals(listOf("Pocillo de inspección", "Zanja 0,60"), repo.tiposExcavacion().first().map { it.nombre })

        repo.guardarTipoExcavacion(guardado.copy(profundidad = 2.0))
        assertEquals(2.0, repo.tipoExcavacion(id)!!.profundidad!!, 0.0)

        repo.eliminarTipoExcavacion(id)
        assertNull(repo.tipoExcavacion(id))
    }

    @Test
    fun tipoDeExcavacionRechazaNombreRepetidoOVacioYMedidasInvalidas() = runBlocking {
        repo.guardarTipoExcavacion(tipo("Pocillo"))
        for (malo in listOf(tipo("POCILLO"), tipo("   "), tipo("Otro", largo = 0.0))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.guardarTipoExcavacion(malo) } }
        }
        assertEquals(1, repo.tiposExcavacion().first().size)
    }

    @Test
    fun unDiaCerradoNoSeModificaHastaReabrirloConMotivo() = runBlocking {
        val id = repo.guardar(borrador())
        val otroDia = repo.guardar(borrador(fecha = hoy.plusDays(1)))
        assertEquals(1, repo.cerrarDias(hoy, hoy, "María"))

        // Ni agregar, ni editar, ni sacar del día, ni traer a él, ni borrar.
        for (intento in listOf(
            borrador(),
            repo.borrador(id)!!.copy(largo = 9.0),
            repo.borrador(id)!!.copy(fecha = hoy.plusDays(2)),
            repo.borrador(otroDia)!!.copy(fecha = hoy),
        )) {
            val e = assertThrows(ExcavacionInvalida::class.java) { runBlocking { repo.guardar(intento) } }
            assertTrue(Campo.FECHA in e.errores)
        }
        assertThrows(DiaCerrado::class.java) { runBlocking { repo.eliminar(id) } }
        // Volver a cerrar no cambia el cierre original.
        assertEquals(0, repo.cerrarDias(hoy, hoy.plusDays(1), "Otro").minus(1))
        assertEquals("María", repo.cierre(hoy).first()!!.responsable)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.reabrirDia(hoy, "  ") } }
        repo.reabrirDia(hoy, "Corregir medida N° 01")
        assertNull(repo.cierre(hoy).first())
        repo.guardar(repo.borrador(id)!!.copy(largo = 9.0))
        val (cierres, reaperturas) = repo.cierresYReaperturas(hoy, hoy.plusDays(1))
        assertEquals(listOf(hoy.plusDays(1)), cierres.map { it.fecha })
        assertEquals("Corregir medida N° 01", reaperturas.single().motivo)
        assertEquals("María", reaperturas.single().responsableCierre)
    }

    @Test
    fun guardaElOrigenDeCadaFoto() = runBlocking {
        val id = repo.guardar(
            borrador().copy(fotos = listOf(FotoBorrador("a.jpg", 1L, OrigenFoto.CAMARA), FotoBorrador("b.jpg", 2L, OrigenFoto.GALERIA))),
        )
        assertEquals(listOf(OrigenFoto.CAMARA, OrigenFoto.GALERIA), repo.borrador(id)!!.fotos.map { it.origen })
    }

    @Test
    fun traeLosTiposDeTerrenoIniciales() = runBlocking {
        assertEquals(BaseDatos.TIPOS_TERRENO_INICIALES, repo.tiposTerreno().first().map { it.nombre })
    }

    @Test
    fun numeraCorrelativoPorSectorLadoYSentido() = runBlocking {
        repo.guardar(borrador())
        repo.guardar(borrador())
        repo.guardar(borrador(sentido = Sentido.CONCEPCION_CHILLAN))
        // Mismo sector escrito distinto: es el mismo frente; y la numeración sigue al día siguiente.
        repo.guardar(borrador(sector = "  sector   a ", fecha = hoy.plusDays(1)))

        assertEquals(4, repo.siguienteNumero("Sector A", Lado.NORTE, Sentido.CHILLAN_CONCEPCION))
        assertEquals(2, repo.siguienteNumero("Sector A", Lado.NORTE, Sentido.CONCEPCION_CHILLAN))
        assertEquals(1, repo.siguienteNumero("Sector A", Lado.SUR, Sentido.CHILLAN_CONCEPCION))
        assertEquals(1, repo.siguienteNumero("Sector Z", Lado.NORTE, Sentido.CHILLAN_CONCEPCION))
        assertEquals(1, repo.sectores().first().size)
    }

    @Test
    fun noPermiteRepetirUnNumeroEnElFrente() = runBlocking {
        repo.guardar(borrador(numero = 7))
        val error = assertThrows(ExcavacionInvalida::class.java) { runBlocking { repo.guardar(borrador(numero = 7)) } }
        assertTrue(Campo.NUMERO in error.errores)
        // En otro lado sí.
        repo.guardar(borrador(lado = Lado.SUR, numero = 7))
        Unit
    }

    @Test
    fun editarConservaElNumeroYReemplazaLasFotos() = runBlocking {
        val id = repo.guardar(borrador().copy(fotos = fotos("a.jpg", "b.jpg")))
        repo.guardar(borrador().copy(id = id, largo = 5.0, fotos = fotos("c.jpg")))

        val guardada = repo.excavacion(id)!!
        assertEquals(1, guardada.numero)
        assertEquals(5.0, guardada.largo, 0.0)
        assertEquals(listOf("c.jpg"), repo.fotos(id).map { it.archivo })
    }

    @Test
    fun resumenDelDiaAgrupaYSuma() = runBlocking {
        repo.guardar(borrador())
        repo.guardar(borrador(lado = Lado.SUR))
        repo.guardar(borrador(sector = "Sector B"))
        repo.guardar(borrador(fecha = hoy.minusDays(1)))

        val resumen = repo.resumenDelDia(hoy).first()
        assertEquals(3, resumen.cantidad)
        assertEquals(3 * 7.2, resumen.volumen, 1e-9)
        assertEquals(listOf("Sector A", "Sector B"), resumen.sectores.map { it.nombre })
        assertEquals(listOf(Lado.NORTE, Lado.SUR), resumen.sectores[0].lados.map { it.lado })
        assertEquals("Suelo Tipo I", resumen.excavaciones.first().tipoTerreno)
    }

    @Test
    fun reporteFiltraPorRangoYSector() = runBlocking {
        repo.guardar(borrador(fecha = hoy.minusDays(3)))
        repo.guardar(borrador(fecha = hoy))
        repo.guardar(borrador(sector = "Sector B", fecha = hoy))
        val sectorB = repo.sectores().first().first { it.nombre == "Sector B" }.id

        assertEquals(2, repo.paraReporte(hoy, hoy).size)
        assertEquals(3, repo.paraReporte(hoy, hoy.minusDays(5)).size)
        assertEquals(listOf("Sector B"), repo.paraReporte(hoy, hoy, sectorB).map { it.sector })
    }

    @Test
    fun eliminarBorraLasFotosYDevuelveSusArchivos() = runBlocking {
        val id = repo.guardar(borrador().copy(fotos = fotos("a.jpg", "b.jpg", "c.jpg")))
        assertEquals(3, repo.paraReporte(hoy, hoy).single().fotos)
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), repo.eliminar(id))
        assertNull(repo.excavacion(id))
        assertTrue(repo.fotos(id).isEmpty())
    }

    @Test
    fun tiposDeTerrenoSeAgreganYDesactivan() = runBlocking {
        val id = repo.agregarTipoTerreno("Arcilla expansiva")
        assertEquals("Arcilla expansiva", repo.tiposTerreno().first().last().nombre)
        repo.desactivarTipoTerreno(id)
        assertTrue(repo.tiposTerreno().first().none { it.id == id })
        // Escribirlo de nuevo (con otras mayúsculas) lo reactiva en vez de duplicarlo.
        assertEquals(id, repo.agregarTipoTerreno("arcilla EXPANSIVA"))
    }

    @Test
    fun unTipoDeTerrenoEnUsoNoSePuedeBorrar() = runBlocking {
        repo.guardar(borrador())
        assertThrows(SQLiteConstraintException::class.java) {
            bd.openHelper.writableDatabase.execSQL("DELETE FROM tipos_terreno WHERE id = $suelo")
        }
        Unit
    }
}
