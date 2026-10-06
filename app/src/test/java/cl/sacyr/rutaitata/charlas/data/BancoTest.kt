package cl.sacyr.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BancoTest {

    private val banco = BancoParser.parse(File("src/main/assets/charlas.json").readText())

    @Test
    fun cubreCuatroSemanasDeLunesASabado() {
        assertEquals(24, banco.charlas.size)
        for (semana in 1..4) for (dia in 1..6) {
            assertNotNull("Falta semana $semana día $dia", banco.charla(semana, dia))
        }
        assertEquals(24, banco.charlas.map { it.id }.toSet().size)
    }

    @Test
    fun cadaCharlaTieneLaEstructuraCompleta() {
        banco.charlas.forEach {
            assertEquals("Charla ${it.id}", 3, it.checklist.size)
            assertTrue(it.titulo.isNotBlank() && it.porQue.isNotBlank())
            assertTrue(it.normativa.isNotBlank() && it.reglaOro.isNotBlank())
            assertTrue(it.preguntaCierre.endsWith("?"))
            banco.especialidad(it.especialidadId)
        }
    }

    @Test
    fun cadaSemanaIncluyeTodasLasEspecialidades() {
        val todas = banco.especialidades.map { it.id }.toSet()
        banco.charlas.groupBy { it.semana }.forEach { (semana, charlas) ->
            assertEquals("Semana $semana", todas, charlas.map { it.especialidadId }.toSet())
        }
    }

    @Test
    fun elBancoIncluidoEsValidoYTieneVersion() {
        assertEquals(emptyList<String>(), BancoParser.validar(banco))
        assertTrue(banco.version >= 1)
    }

    @Test
    fun validarDetectaErroresQueRomperianLaApp() {
        val charla = banco.charlas.first()
        val roto = banco.copy(
            charlas = banco.charlas + charla.copy(id = 99, especialidadId = "XX", semana = 5, checklist = emptyList()),
        )
        val errores = BancoParser.validar(roto)
        assertTrue(errores.any { "especialidad desconocida" in it })
        assertTrue(errores.any { "semana fuera de rango" in it })
        assertTrue(errores.any { "checklist vacío" in it })
        assertTrue(BancoParser.validar(banco.copy(charlas = banco.charlas + charla)).any { "repetido" in it })
    }

    @Test
    fun primeraCharlaEsAtropelloHombreMaquina() {
        val primera = banco.charla(1, 1)!!
        assertEquals("MP", primera.especialidadId)
        assertTrue(primera.titulo.contains("maquinaria"))
    }

    @Test
    fun textoCompartidoIncluyeTodasLasSecciones() {
        val texto = banco.charlas.first().comoTexto(banco)
        listOf("EL PORQUÉ", "PUNTOS DE CONTROL", "RESPALDO ESTÁNDAR", "PREGUNTA DE CIERRE", "☐ 3.").forEach {
            assertTrue("Falta '$it'", texto.contains(it))
        }
    }
}
