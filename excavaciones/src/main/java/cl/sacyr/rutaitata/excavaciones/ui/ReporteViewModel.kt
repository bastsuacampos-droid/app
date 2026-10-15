package cl.sacyr.rutaitata.excavaciones.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.dominio.Volumen
import cl.sacyr.rutaitata.excavaciones.reporte.DatosReporte
import cl.sacyr.rutaitata.excavaciones.reporte.FotoReporte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Lo que entraría al reporte con los filtros actuales. */
data class Previa(val excavaciones: Int, val volumen: Double, val fotos: Int, val sinFoto: Int)

sealed interface EstadoGeneracion {
    data object Inactivo : EstadoGeneracion
    data object Generando : EstadoGeneracion
    data class Listo(val archivo: File, val paginas: Int) : EstadoGeneracion
    data class Fallo(val mensaje: String) : EstadoGeneracion
}

class ReporteViewModel(private val app: AppExcavaciones, private val guardado: SavedStateHandle) : ViewModel() {

    private val repo = app.repositorio
    private val zona = ZoneId.systemDefault()

    private val fechaInicial = guardado.get<Long>("fecha")?.takeIf { it != Long.MIN_VALUE } ?: LocalDate.now().toEpochDay()
    val desde: StateFlow<Long> = guardado.getStateFlow(DESDE, fechaInicial)
    val hasta: StateFlow<Long> = guardado.getStateFlow(HASTA, fechaInicial)
    /** -1: todos los sectores. */
    val sectorId: StateFlow<Long> = guardado.getStateFlow(SECTOR, -1L)
    val responsable: StateFlow<String> = guardado.getStateFlow(RESPONSABLE, app.preferencias.responsable)
    val incluirFotos: StateFlow<Boolean> = guardado.getStateFlow(FOTOS, app.preferencias.incluirFotos)

    val sectores = repo.sectores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val previa: StateFlow<Previa?> = combine(desde, hasta, sectorId) { d, h, s -> Triple(d, h, s) }
        .mapLatest { (d, h, s) ->
            val lista = repo.paraReporte(LocalDate.ofEpochDay(d), LocalDate.ofEpochDay(h), s.takeIf { it > 0 })
            Previa(lista.size, Volumen.total(lista), lista.sumOf { it.fotos }, lista.count { it.fotos == 0 })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _generacion = MutableStateFlow<EstadoGeneracion>(EstadoGeneracion.Inactivo)
    val generacion: StateFlow<EstadoGeneracion> = _generacion

    fun periodo(desde: LocalDate, hasta: LocalDate) {
        guardado[DESDE] = minOf(desde, hasta).toEpochDay()
        guardado[HASTA] = maxOf(desde, hasta).toEpochDay()
        _generacion.value = EstadoGeneracion.Inactivo
    }

    fun sector(id: Long?) {
        guardado[SECTOR] = id ?: -1L
        _generacion.value = EstadoGeneracion.Inactivo
    }

    fun responsable(nombre: String) {
        guardado[RESPONSABLE] = nombre
    }

    fun incluirFotos(si: Boolean) {
        guardado[FOTOS] = si
        app.preferencias.incluirFotos = si
        _generacion.value = EstadoGeneracion.Inactivo
    }

    fun generar() {
        if (_generacion.value == EstadoGeneracion.Generando) return
        app.preferencias.responsable = responsable.value
        val d = LocalDate.ofEpochDay(desde.value)
        val h = LocalDate.ofEpochDay(hasta.value)
        val s = sectorId.value.takeIf { it > 0 }
        val conFotos = incluirFotos.value
        viewModelScope.launch {
            _generacion.value = EstadoGeneracion.Generando
            _generacion.value = runCatching {
                withContext(Dispatchers.IO) {
                    val lista = repo.paraReporte(d, h, s)
                    val fotos = if (conFotos) fotosParaReporte(lista.map { it.id }) else emptyMap()
                    val datos = DatosReporte(
                        desde = d,
                        hasta = h,
                        sector = s?.let { id -> lista.firstOrNull()?.sector ?: sectores.value.firstOrNull { it.id == id }?.nombre },
                        responsable = responsable.value,
                        excavaciones = lista,
                        incluirFotos = conFotos,
                        fotos = fotos,
                    )
                    app.generadorPdf.generar(datos)
                }
            }.fold(
                onSuccess = { EstadoGeneracion.Listo(it.archivo, it.paginas) },
                onFailure = { EstadoGeneracion.Fallo(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /** Solo las fotos cuyo archivo existe y se puede leer (con su tamaño, para maquetar). */
    private suspend fun fotosParaReporte(ids: List<Long>): Map<Long, List<FotoReporte>> =
        repo.fotosDe(ids).mapValues { (_, fotos) ->
            fotos.mapNotNull { f ->
                app.fotos.medidas(f.archivo)?.let { (ancho, alto) ->
                    FotoReporte(
                        ruta = app.fotos.archivo(f.archivo).path,
                        ancho = ancho,
                        alto = alto,
                        tomadaEn = f.tomadaEn?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zona) },
                    )
                }
            }
        }

    private companion object {
        const val DESDE = "desde"
        const val HASTA = "hasta"
        const val SECTOR = "sector"
        const val RESPONSABLE = "responsable"
        const val FOTOS = "incluir_fotos"
    }
}
