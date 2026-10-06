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
        return Banco(raiz.getString("proyecto"), especialidades, charlas)
    }

    private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
