package cl.sacyr.rutaitata.charlas.data

import org.json.JSONArray
import org.json.JSONObject

object BancoParser {

    fun parse(json: String): Banco {
        val raiz = JSONObject(json)
        val especialidades = raiz.getJSONArray("especialidades").objetos().map {
            Especialidad(
                id = it.getString("id"),
                nombre = it.getString("nombre"),
                colorHex = it.getString("color"),
            )
        }
        val orden = especialidades.withIndex().associate { it.value.id to it.index }
        val charlas = raiz.getJSONArray("charlas").objetos().map {
            Charla(
                id = it.getInt("id"),
                codigo = it.optString("codigo").ifBlank { it.getInt("id").toString() },
                especialidadId = it.getString("especialidad"),
                titulo = it.getString("titulo"),
                porQue = it.getString("porQue"),
                checklist = it.getJSONArray("checklist").let { arr ->
                    List(arr.length()) { i -> arr.getString(i) }
                },
                normativa = it.getString("normativa"),
                reglaOro = it.getString("reglaOro"),
                preguntaCierre = it.getString("preguntaCierre"),
            )
        }.sortedWith(compareBy({ orden[it.especialidadId] ?: Int.MAX_VALUE }, { it.codigo }))
        val plan = raiz.optJSONArray("plan")?.objetos().orEmpty().map {
            EntradaPlan(semana = it.getInt("semana"), dia = it.getInt("dia"), charlaId = it.getInt("charla"))
        }.sortedWith(compareBy({ it.semana }, { it.dia }))
        val actividades = raiz.optJSONArray("actividades")?.objetos().orEmpty().map {
            Actividad(
                id = it.getString("id"),
                nombre = it.getString("nombre"),
                palabras = it.getJSONArray("palabras").let { arr -> List(arr.length()) { i -> arr.getString(i) } },
                especialidades = it.optJSONArray("especialidades")
                    ?.let { arr -> List(arr.length()) { i -> arr.getString(i) } }.orEmpty(),
            )
        }
        return Banco(
            proyecto = raiz.getString("proyecto"),
            version = raiz.getInt("version"),
            fecha = raiz.optString("fecha"),
            novedades = raiz.optString("novedades"),
            especialidades = especialidades,
            charlas = charlas,
            plan = plan,
            actividades = actividades,
        )
    }

    /**
     * Revisa que el banco se pueda mostrar sin problemas. Devuelve la lista de
     * errores encontrados; vacía si el banco es válido.
     */
    fun validar(banco: Banco): List<String> {
        val errores = mutableListOf<String>()
        if (banco.charlas.isEmpty()) errores += "El banco no tiene charlas"
        val especialidades = banco.especialidades.map { it.id }.toSet()
        banco.charlas.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            errores += "Id de charla repetido: $it"
        }
        banco.charlas.groupBy { it.codigo }.filterValues { it.size > 1 }.keys.forEach {
            errores += "Código de charla repetido: $it"
        }
        banco.charlas.forEach { c ->
            val ref = "Charla ${c.codigo}"
            if (c.especialidadId !in especialidades) errores += "$ref: especialidad desconocida '${c.especialidadId}'"
            if (c.checklist.isEmpty() || c.checklist.any { it.isBlank() }) errores += "$ref: checklist vacío"
            if (listOf(c.titulo, c.porQue, c.normativa, c.reglaOro, c.preguntaCierre).any { it.isBlank() }) {
                errores += "$ref: hay campos de texto vacíos"
            }
        }
        val ids = banco.charlas.map { it.id }.toSet()
        banco.plan.groupBy { it.semana to it.dia }.filterValues { it.size > 1 }.keys.forEach {
            errores += "Plan: más de una charla en semana ${it.first} día ${it.second}"
        }
        banco.plan.forEach { e ->
            val ref = "Plan semana ${e.semana} día ${e.dia}"
            if (e.semana !in 1..SEMANAS_CICLO) errores += "$ref: semana fuera de rango (1-$SEMANAS_CICLO)"
            if (e.dia !in 1..NOMBRES_DIA.size) errores += "$ref: día fuera de rango (1-${NOMBRES_DIA.size})"
            if (e.charlaId !in ids) errores += "$ref: charla ${e.charlaId} no existe"
        }
        banco.actividades.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            errores += "Id de actividad repetido: $it"
        }
        banco.actividades.forEach { a ->
            if (a.nombre.isBlank() || a.palabras.none { it.isNotBlank() }) errores += "Actividad ${a.id}: sin nombre o sin palabras"
            a.especialidades.filter { it !in especialidades }.forEach {
                errores += "Actividad ${a.id}: especialidad desconocida '$it'"
            }
        }
        banco.especialidades.forEach {
            if (!Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})").matches(it.colorHex)) errores += "Especialidad ${it.id}: color inválido"
        }
        return errores
    }

    private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
