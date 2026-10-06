package cl.sacyr.rutaitata.charlas.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Plan de charlas de una semana armado por el capataz.
 *
 * @property lunes lunes de la semana planificada.
 * @property especialidades especialidades elegidas, en el orden en que se reparten.
 * @property dias días con charla (1 = lunes … 6 = sábado).
 * @property charlas charla asignada a cada día; un día sin entrada no tuvo charla disponible.
 * @property actividades actividad anotada por el capataz para cada día (opcional).
 */
data class PlanSemanal(
    val lunes: LocalDate,
    val especialidades: List<String>,
    val dias: List<Int>,
    val charlas: Map<Int, Int>,
    val actividades: Map<Int, String> = emptyMap(),
) {
    fun fecha(dia: Int): LocalDate = lunes.plusDays(dia - 1L)
}

object Planificador {

    /**
     * Reparte las [especialidades] entre los [dias] en el orden elegido y asigna a cada día
     * la primera charla de esa especialidad que no se haya dictado ni planificado en el mes.
     * Si una especialidad se agotó, usa otra de las elegidas.
     */
    fun generar(
        banco: Banco,
        lunes: LocalDate,
        especialidades: List<String>,
        dias: List<Int>,
        usos: Collection<Uso>,
        otrosPlanes: Collection<PlanSemanal>,
    ): PlanSemanal {
        var plan = PlanSemanal(lunes, especialidades, dias.sorted(), emptyMap())
        if (especialidades.isEmpty()) return plan
        plan.dias.forEachIndexed { i, dia ->
            val ocupadas = ocupadas(plan, dia, usos, otrosPlanes)
            val preferida = i % especialidades.size
            val elegida = (especialidades.indices)
                .map { especialidades[(preferida + it) % especialidades.size] }
                .firstNotNullOfOrNull { esp -> banco.charlasDe(esp).firstOrNull { it.id !in ocupadas } }
            if (elegida != null) plan = plan.copy(charlas = plan.charlas + (dia to elegida.id))
        }
        return plan
    }

    /**
     * Cambia la charla de [dia] por otra disponible de [especialidad]. Con [azar] elige una al
     * azar; sin él, la siguiente en orden (llamarla varias veces recorre las opciones).
     * Devuelve null si no hay otra disponible.
     */
    fun cambiar(
        banco: Banco,
        plan: PlanSemanal,
        dia: Int,
        especialidad: String,
        usos: Collection<Uso>,
        otrosPlanes: Collection<PlanSemanal>,
        azar: Random? = null,
    ): PlanSemanal? {
        val opciones = banco.charlasDe(especialidad)
        if (opciones.isEmpty()) return null
        val actual = plan.charlas[dia]
        val inicio = opciones.indexOfFirst { it.id == actual } + 1
        val ocupadas = ocupadas(plan, dia, usos, otrosPlanes)
        val libres = opciones.indices
            .map { opciones[(inicio + it) % opciones.size] }
            .filter { it.id != actual && it.id !in ocupadas }
        val nueva = (if (azar != null) libres.randomOrNull(azar) else libres.firstOrNull()) ?: return null
        return asignar(plan, dia, nueva.id)
    }

    /** Pone [charlaId] en [dia]; si el día no estaba en el plan, lo agrega. */
    fun asignar(plan: PlanSemanal, dia: Int, charlaId: Int): PlanSemanal = plan.copy(
        dias = (plan.dias + dia).distinct().sorted(),
        charlas = plan.charlas + (dia to charlaId),
    )

    /** Anota la actividad del día; texto vacío la borra. */
    fun anotarActividad(plan: PlanSemanal, dia: Int, actividad: String): PlanSemanal = plan.copy(
        actividades = if (actividad.isBlank()) plan.actividades - dia else plan.actividades + (dia to actividad.trim()),
    )

    /**
     * Charlas que no conviene poner en [dia]: las dictadas en el mes de ese día, las planificadas
     * en otras semanas para ese mismo mes y las de los demás días de esta semana.
     */
    internal fun ocupadas(
        plan: PlanSemanal,
        dia: Int,
        usos: Collection<Uso>,
        otrosPlanes: Collection<PlanSemanal>,
    ): Set<Int> {
        val mes = YearMonth.from(plan.fecha(dia))
        val dictadas = usos.filter { YearMonth.from(it.fecha) == mes }.map { it.charlaId }
        val planificadas = otrosPlanes
            .filter { it.lunes != plan.lunes }
            .flatMap { otro -> otro.charlas.filterKeys { YearMonth.from(otro.fecha(it)) == mes }.values }
        val estaSemana = plan.charlas.filterKeys { it != dia }.values
        return (dictadas + planificadas + estaSemana).toSet()
    }

    /** Texto para enviar el plan por WhatsApp o correo. */
    fun comoTexto(plan: PlanSemanal, banco: Banco): String = buildString {
        val f = DateTimeFormatter.ofPattern("dd/MM")
        appendLine("PLAN DE CHARLAS DE 5 MINUTOS – ${banco.proyecto}")
        appendLine("Semana del ${plan.lunes.format(f)} al ${plan.fecha(6).format(f)}")
        appendLine()
        plan.dias.forEach { dia ->
            val charla = plan.charlas[dia]?.let(banco::charla)
            append("${nombreDia(dia)} ${plan.fecha(dia).format(f)}: ")
            append(charla?.let { "${it.codigo} · ${it.titulo}" } ?: "sin charla asignada")
            appendLine(plan.actividades[dia]?.let { " (actividad: $it)" }.orEmpty())
        }
    }.trimEnd()

    fun aJson(plan: PlanSemanal): String = JSONObject()
        .put("lunes", plan.lunes.toString())
        .put("especialidades", JSONArray(plan.especialidades))
        .put("dias", JSONArray(plan.dias))
        .put("charlas", JSONObject().apply { plan.charlas.forEach { (dia, id) -> put(dia.toString(), id) } })
        .put("actividades", JSONObject().apply { plan.actividades.forEach { (dia, a) -> put(dia.toString(), a) } })
        .toString()

    fun deJson(texto: String): PlanSemanal? = runCatching {
        val o = JSONObject(texto)
        val especialidades = o.getJSONArray("especialidades")
        val dias = o.getJSONArray("dias")
        val charlas = o.getJSONObject("charlas")
        PlanSemanal(
            lunes = LocalDate.parse(o.getString("lunes")),
            especialidades = List(especialidades.length()) { especialidades.getString(it) },
            dias = List(dias.length()) { dias.getInt(it) },
            charlas = charlas.keys().asSequence().associate { it.toInt() to charlas.getInt(it) },
            actividades = o.optJSONObject("actividades")
                ?.let { a -> a.keys().asSequence().associate { it.toInt() to a.getString(it) } }
                .orEmpty(),
        )
    }.getOrNull()
}
