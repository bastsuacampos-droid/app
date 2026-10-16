package cl.sacyr.rutaitata.excavaciones.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.dominio.Volumen
import cl.sacyr.rutaitata.excavaciones.reporte.DatosReporte
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.CierreDiaEntity
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import cl.sacyr.rutaitata.excavaciones.reporte.CierreReporte
import cl.sacyr.rutaitata.excavaciones.reporte.FotoReporte
import cl.sacyr.rutaitata.excavaciones.reporte.ReaperturaReporte
import cl.sacyr.rutaitata.excavaciones.reporte.Planilla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
data class Previa(val excavaciones: Int, val volumen: Double, val fotos: Int, val sinFoto: Int, val dias: Set<LocalDate> = emptySet())

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
    /** Cerrar los días abiertos del período al generar: el reporte sale CERRADO y no BORRADOR. */
    val cerrarAlGenerar: StateFlow<Boolean> = guardado.getStateFlow(CERRAR, app.preferencias.cerrarAlGenerar)

    val sectores = repo.sectores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val previa: StateFlow<Previa?> = combine(desde, hasta, sectorId) { d, h, s -> Triple(d, h, s) }
        .mapLatest { (d, h, s) ->
            val lista = repo.paraReporte(LocalDate.ofEpochDay(d), LocalDate.ofEpochDay(h), s.takeIf { it > 0 })
            Previa(lista.size, Volumen.total(lista), lista.sumOf { it.fotos }, lista.count { it.fotos == 0 }, lista.map { it.fecha }.toSet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Días cerrados del período elegido. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cierres: StateFlow<List<CierreDiaEntity>> = combine(desde, hasta) { d, h -> d to h }
        .flatMapLatest { (d, h) -> repo.cierresEntre(LocalDate.ofEpochDay(d), LocalDate.ofEpochDay(h)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _generacion = MutableStateFlow<EstadoGeneracion>(EstadoGeneracion.Inactivo)
    val generacion: StateFlow<EstadoGeneracion> = _generacion

    fun periodo(desde: LocalDate, hasta: LocalDate) {
        guardado[DESDE] = minOf(desde, hasta).toEpochDay()
        guardado[HASTA] = maxOf(desde, hasta).toEpochDay()
        _generacion.value = EstadoGeneracion.Inactivo
        _planilla.value = null
    }

    fun sector(id: Long?) {
        guardado[SECTOR] = id ?: -1L
        _generacion.value = EstadoGeneracion.Inactivo
        _planilla.value = null
    }

    fun responsable(nombre: String) {
        guardado[RESPONSABLE] = nombre
    }

    fun incluirFotos(si: Boolean) {
        guardado[FOTOS] = si
        app.preferencias.incluirFotos = si
        _generacion.value = EstadoGeneracion.Inactivo
    }

    fun cerrarAlGenerar(si: Boolean) {
        guardado[CERRAR] = si
        app.preferencias.cerrarAlGenerar = si
    }

    /**
     * Genera el PDF. Con [cerrarDias], antes cierra los días del período (reporte definitivo): sus
     * excavaciones quedan fijas y el PDF sale como CERRADO.
     */
    fun generar(cerrarDias: Boolean = false) {
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
                    if (cerrarDias) repo.cerrarDias(d, h, responsable.value)
                    val (cierres, reaperturas) = repo.cierresYReaperturas(d, h)
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
                        cierres = cierres.map { CierreReporte(it.fecha, local(it.cerradoEn), it.responsable) },
                        reaperturas = reaperturas.map { ReaperturaReporte(it.fecha, local(it.cerradoEn), local(it.reabiertoEn), it.motivo) },
                    )
                    app.generadorPdf.generar(datos)
                }
            }.fold(
                onSuccess = { EstadoGeneracion.Listo(it.archivo, it.paginas) },
                onFailure = { EstadoGeneracion.Fallo(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    private val _planilla = MutableStateFlow<File?>(null)
    /** Última planilla CSV exportada con los filtros actuales. */
    val planilla: StateFlow<File?> = _planilla

    /** Exporta la planilla (CSV para Excel) del período y sector elegidos. */
    fun exportarPlanilla() {
        val d = LocalDate.ofEpochDay(desde.value)
        val h = LocalDate.ofEpochDay(hasta.value)
        val s = sectorId.value.takeIf { it > 0 }
        viewModelScope.launch {
            _planilla.value = runCatching {
                withContext(Dispatchers.IO) {
                    val carpeta = File(app.filesDir, "reportes").apply { mkdirs() }
                    File(carpeta, Planilla.nombreArchivo(d, h)).apply { writeText(Planilla.csv(repo.paraReporte(d, h, s))) }
                }
            }.getOrNull()
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
                        tomadaEn = f.tomadaEn?.let(::local),
                        origen = OrigenFoto.desdeCodigo(f.origen),
                    )
                }
            }
        }

    private fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zona)

    private companion object {
        const val DESDE = "desde"
        const val HASTA = "hasta"
        const val SECTOR = "sector"
        const val RESPONSABLE = "responsable"
        const val FOTOS = "incluir_fotos"
        const val CERRAR = "cerrar_al_generar"
    }
}
