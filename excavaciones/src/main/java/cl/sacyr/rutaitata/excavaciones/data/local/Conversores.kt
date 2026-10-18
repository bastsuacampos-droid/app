package cl.sacyr.rutaitata.excavaciones.data.local

import androidx.room.TypeConverter
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import java.time.LocalDate

/** Fechas como días desde 1970-01-01 (se comparan y ordenan como número); lado y sentido por su código. */
class Conversores {
    @TypeConverter fun fechaANumero(fecha: LocalDate?): Long? = fecha?.toEpochDay()

    @TypeConverter fun numeroAFecha(dias: Long?): LocalDate? = dias?.let(LocalDate::ofEpochDay)

    @TypeConverter fun ladoACodigo(lado: Lado?): String? = lado?.codigo

    @TypeConverter fun codigoALado(codigo: String?): Lado? = codigo?.let(Lado::desdeCodigo)

    @TypeConverter fun sentidoACodigo(sentido: Sentido?): String? = sentido?.codigo

    @TypeConverter fun codigoASentido(codigo: String?): Sentido? = codigo?.let(Sentido::desdeCodigo)
}
