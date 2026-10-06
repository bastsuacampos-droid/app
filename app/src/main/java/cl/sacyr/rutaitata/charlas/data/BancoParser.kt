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
        val charlas = raiz.getJSONArray("charlas").objetos().map {
            Charla(
                id = it.getInt("id"),
                semana = it.getInt("semana"),
                dia = it.getInt("dia"),
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
        }.sortedWith(compareBy({ it.semana }, { it.dia }))
        return Banco(
            proyecto = raiz.getString("proyecto"),
            version = raiz.getInt("version"),
            fecha = raiz.optString("fecha"),
            novedades = raiz.optString("novedades"),
            especialidades = especialidades,
            charlas = charlas,
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
        banco.charlas.groupBy { it.semana to it.dia }.filterValues { it.size > 1 }.keys.forEach {
            errores += "Hay más de una charla en semana ${it.first} día ${it.second}"
        }
        banco.charlas.forEach { c ->
            val ref = "Charla ${c.id}"
            if (c.semana !in 1..SEMANAS_CICLO) errores += "$ref: semana fuera de rango (1-$SEMANAS_CICLO)"
            if (c.dia !in 1..NOMBRES_DIA.size) errores += "$ref: día fuera de rango (1-${NOMBRES_DIA.size})"
            if (c.especialidadId !in especialidades) errores += "$ref: especialidad desconocida '${c.especialidadId}'"
            if (c.checklist.isEmpty() || c.checklist.any { it.isBlank() }) errores += "$ref: checklist vacío"
            if (listOf(c.titulo, c.porQue, c.normativa, c.reglaOro, c.preguntaCierre).any { it.isBlank() }) {
                errores += "$ref: hay campos de texto vacíos"
            }
        }
        banco.especialidades.forEach {
            if (!Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})").matches(it.colorHex)) errores += "Especialidad ${it.id}: color inválido"
        }
        return errores
    }

    private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
