package cl.sacyr.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BancoTest {

    private val banco = BancoParser.parse(File("src/main/assets/charlas.json").readText())

    @Test
    fun cadaEspecialidadTieneAlMenos25Charlas() {
        banco.especialidades.forEach {
            assertTrue("${it.nombre} tiene ${banco.charlasDe(it.id).size}", banco.charlasDe(it.id).size >= 25)
        }
        assertEquals(banco.charlas.size, banco.charlas.map { it.id }.toSet().size)
    }

    @Test
    fun incluyeLasEspecialidadesDeObrasCiviles() {
        val nombres = banco.especialidades.map { it.nombre }
        listOf("Carpintería", "Enfierradura", "Hormigonado", "Movimiento de Tierra").forEach {
            assertTrue("Falta $it", it in nombres)
        }
    }

    @Test
    fun noHayTitulosRepetidos() {
        val repetidos = banco.charlas.groupBy { it.titulo.lowercase() }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), repetidos)
    }

    @Test
    fun codigosCorrelativosPorEspecialidad() {
        banco.especialidades.forEach { esp ->
            val codigos = banco.charlasDe(esp.id).map { it.codigo }
            assertEquals(List(codigos.size) { "${esp.id}-%02d".format(it + 1) }, codigos)
        }
    }

    @Test
    fun cadaCharlaTieneLaEstructuraCompleta() {
        banco.charlas.forEach {
            assertEquals("Charla ${it.codigo}", 3, it.checklist.size)
            assertTrue(it.preguntaCierre.endsWith("?") || it.preguntaCierre.endsWith("."))
        }
    }

    @Test
    fun elPlanCubreCuatroSemanasDeLunesASabado() {
        assertEquals(24, banco.plan.size)
        for (semana in 1..4) for (dia in 1..6) {
            assertNotNull("Falta semana $semana día $dia", banco.charla(semana, dia))
        }
        // Plan sugerido original: cada semana cubre las 5 especialidades base.
        val base = setOf("MP", "CT", "AS", "OA", "RT")
        banco.plan.groupBy { it.semana }.forEach { (semana, entradas) ->
            val especialidades = entradas.mapNotNull { banco.charla(it.charlaId)?.especialidadId }.toSet()
            assertEquals("Semana $semana", base, especialidades)
        }
    }

    @Test
    fun elBancoIncluidoEsValidoYTieneVersion() {
        assertEquals(emptyList<String>(), BancoParser.validar(banco))
        assertTrue(banco.version >= 6)
        assertTrue(banco.actividades.size >= 40)
    }

    @Test
    fun validarDetectaErroresQueRomperianLaApp() {
        val charla = banco.charlas.first()
        val roto = banco.copy(
            charlas = banco.charlas + charla.copy(id = 9999, codigo = "XX-01", especialidadId = "XX", checklist = emptyList()),
            plan = banco.plan + EntradaPlan(semana = 5, dia = 1, charlaId = 12345),
        )
        val errores = BancoParser.validar(roto)
        assertTrue(errores.any { "especialidad desconocida" in it })
        assertTrue(errores.any { "checklist vacío" in it })
        assertTrue(errores.any { "semana fuera de rango" in it })
        assertTrue(errores.any { "no existe" in it })
        assertTrue(BancoParser.validar(banco.copy(charlas = banco.charlas + charla)).any { "repetido" in it })
    }

    @Test
    fun primeraCharlaDelPlanEsAtropelloHombreMaquina() {
        val primera = banco.charla(1, 1)!!
        assertEquals("MP-01", primera.codigo)
        assertTrue(primera.titulo.contains("maquinaria"))
    }

    @Test
    fun textoCompartidoIncluyeTodasLasSecciones() {
        val charla = banco.charlas.first()
        val texto = charla.comoTexto(banco)
        listOf(charla.codigo, "EL PORQUÉ", "PUNTOS DE CONTROL", "RESPALDO ESTÁNDAR", "PREGUNTA DE CIERRE", "☐ 3.").forEach {
            assertTrue("Falta '$it'", texto.contains(it))
        }
    }
}
