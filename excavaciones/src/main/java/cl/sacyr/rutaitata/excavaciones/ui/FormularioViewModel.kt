package cl.sacyr.rutaitata.excavaciones.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.sacyr.rutaitata.excavaciones.data.ArchivosFotos
import cl.sacyr.rutaitata.excavaciones.data.Preferencias
import cl.sacyr.rutaitata.excavaciones.data.ProveedorUbicacion
import cl.sacyr.rutaitata.excavaciones.data.RepositorioExcavaciones
import cl.sacyr.rutaitata.excavaciones.data.ResultadoUbicacion
import cl.sacyr.rutaitata.excavaciones.data.UltimoFrente
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Campo
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionInvalida
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.MAX_FOTOS
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import cl.sacyr.rutaitata.excavaciones.dominio.ValidadorExcavacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Serializable
import java.time.LocalDate

/**
 * Lo que se está escribiendo en el formulario, tal cual (las medidas como texto, para aceptar
 * "1," a medio escribir). Es Serializable porque vive en el SavedStateHandle: si Android cierra la
 * app mientras la cámara está abierta, al volver el formulario sigue igual.
 */
data class EstadoFormulario(
    val id: Long = 0,
    val fecha: Long = LocalDate.now().toEpochDay(),
    val sector: String = "",
    val lado: Lado? = null,
    val sentido: Sentido? = null,
    /** Vacío: número automático (el siguiente del frente). */
    val numero: String = "",
    val progresiva: String = "",
    val largo: String = "",
    val ancho: String = "",
    val profundidad: String = "",
    val tipoTerrenoId: Long? = null,
    /** Tipo de excavación preestablecido con que se llenaron las medidas (null: ingreso manual). */
    val tipoExcavacionId: Long? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val precision: Float? = null,
    val observaciones: String = "",
    val fotos: List<FotoBorrador> = emptyList(),
    /** Fotos que tenía al abrirlo: las que se quiten se borran al guardar. */
    val fotosOriginales: List<String> = emptyList(),
    /** Archivo que la app de cámara está escribiendo. */
    val capturaPendiente: String? = null,
    val cargado: Boolean = false,
    /** Los errores se muestran después del primer intento de guardar. */
    val mostrarErrores: Boolean = false,
) : Serializable {
    val nueva: Boolean get() = id == 0L

    fun borrador() = BorradorExcavacion(
        id = id,
        fecha = LocalDate.ofEpochDay(fecha),
        sector = sector,
        lado = lado,
        sentido = sentido,
        numero = numero.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 },
        progresiva = progresiva,
        largo = Formato.leerDecimal(largo),
        ancho = Formato.leerDecimal(ancho),
        profundidad = Formato.leerDecimal(profundidad),
        tipoTerrenoId = tipoTerrenoId,
        latitud = latitud,
        longitud = longitud,
        precisionGps = precision,
        observaciones = observaciones,
        fotos = fotos,
    )
}

sealed interface EventoFormulario {
    /** Se guardó y hay que volver al dashboard con este mensaje. */
    data class Terminado(val mensaje: String) : EventoFormulario
    data class Aviso(val texto: String, val accion: AccionAviso? = null) : EventoFormulario
}

enum class AccionAviso { AJUSTES_UBICACION, AJUSTES_APP }

class FormularioViewModel(
    private val repo: RepositorioExcavaciones,
    private val archivos: ArchivosFotos,
    private val ubicacion: ProveedorUbicacion,
    private val preferencias: Preferencias,
    private val contenido: ContentResolver,
    private val guardado: SavedStateHandle,
) : ViewModel() {

    val estado: StateFlow<EstadoFormulario> = guardado.getStateFlow(CLAVE, EstadoFormulario())

    /** Errores que solo detecta la base (número repetido), hasta que se cambie el formulario. */
    private val erroresBase = MutableStateFlow<Map<Campo, String>>(emptyMap())

    val errores: StateFlow<Map<Campo, String>> = estado
        .map { if (it.mostrarErrores) ValidadorExcavacion.validar(it.borrador()) else emptyMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val erroresGuardado: StateFlow<Map<Campo, String>> = erroresBase

    val tiposTerreno = repo.tiposTerreno().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tiposExcavacion = repo.tiposExcavacion().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sectores = repo.sectores().map { l -> l.map { it.nombre } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Número que tendría si se deja automático (null mientras falte sector, lado o sentido). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val siguienteNumero: StateFlow<Int?> = estado
        .map { Triple(it.sector.trim(), it.lado, it.sentido) }
        .distinctUntilChanged()
        .mapLatest { (s, l, se) -> if (s.isEmpty() || l == null || se == null) null else repo.siguienteNumero(s, l, se) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _buscandoUbicacion = MutableStateFlow(false)
    val buscandoUbicacion: StateFlow<Boolean> = _buscandoUbicacion
    private val _procesandoFoto = MutableStateFlow(false)
    val procesandoFoto: StateFlow<Boolean> = _procesandoFoto
    private val _guardando = MutableStateFlow(false)
    val guardando: StateFlow<Boolean> = _guardando

    private val _eventos = Channel<EventoFormulario>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    init {
        if (!estado.value.cargado) viewModelScope.launch { cargar() }
    }

    private suspend fun cargar() {
        val id = guardado.get<Long>("id") ?: 0L
        val inicial = if (id != 0L) {
            val b = repo.borrador(id)
            if (b == null) {
                _eventos.send(EventoFormulario.Terminado("La excavación ya no existe"))
                return
            }
            EstadoFormulario(
                id = b.id,
                fecha = b.fecha.toEpochDay(),
                sector = b.sector,
                lado = b.lado,
                sentido = b.sentido,
                numero = b.numero?.toString().orEmpty(),
                progresiva = b.progresiva.orEmpty(),
                largo = b.largo?.let(::decimalEditable).orEmpty(),
                ancho = b.ancho?.let(::decimalEditable).orEmpty(),
                profundidad = b.profundidad?.let(::decimalEditable).orEmpty(),
                tipoTerrenoId = b.tipoTerrenoId,
                latitud = b.latitud,
                longitud = b.longitud,
                precision = b.precisionGps,
                observaciones = b.observaciones.orEmpty(),
                fotos = b.fotos,
                fotosOriginales = b.fotos.map { it.archivo },
                cargado = true,
            )
        } else {
            val fecha = guardado.get<Long>("fecha")?.takeIf { it != Long.MIN_VALUE } ?: LocalDate.now().toEpochDay()
            nueva(fecha)
        }
        guardado[CLAVE] = inicial
    }

    /**
     * Excavación nueva lista para registrar en serie: fecha del dashboard, último frente usado y,
     * si se eligió uno antes, el último tipo de excavación con sus medidas.
     */
    private suspend fun nueva(fecha: Long): EstadoFormulario {
        val frente = preferencias.ultimoFrente
        val base = EstadoFormulario(
            fecha = fecha,
            sector = frente.sector,
            lado = frente.lado,
            sentido = frente.sentido,
            tipoTerrenoId = frente.tipoTerrenoId,
            cargado = true,
        )
        val tipo = preferencias.ultimoTipoExcavacion?.let { repo.tipoExcavacion(it) }
        if (tipo == null) preferencias.ultimoTipoExcavacion = null // se borró: deja de recordarlo
        return tipo?.let { base.conTipo(it) } ?: base
    }

    /** Elige un tipo de excavación (llena sus medidas) o null para ingresar todo a mano. */
    fun elegirTipoExcavacion(id: Long?) {
        if (id == null) {
            actualizar { copy(tipoExcavacionId = null) }
            return
        }
        viewModelScope.launch {
            val tipo = repo.tipoExcavacion(id) ?: return@launch
            actualizar { conTipo(tipo) }
        }
    }

    /**
     * Guarda un tipo de excavación nuevo (normalmente con las medidas que ya están escritas) y lo
     * deja elegido. Devuelve el error para mostrarlo en el diálogo, o null si se guardó.
     */
    suspend fun guardarTipoExcavacion(tipo: TipoExcavacionEntity): String? =
        runCatching { repo.guardarTipoExcavacion(tipo) }.fold(
            onSuccess = { id ->
                actualizar { conTipo(tipo.copy(id = id)) }
                null
            },
            onFailure = { it.message ?: "No se pudo guardar el tipo" },
        )

    fun actualizar(cambio: EstadoFormulario.() -> EstadoFormulario) {
        guardado[CLAVE] = estado.value.cambio()
        erroresBase.value = emptyMap()
    }

    // ------------------------------------------------------------------ ubicación

    fun obtenerUbicacion() {
        if (_buscandoUbicacion.value) return
        viewModelScope.launch {
            _buscandoUbicacion.value = true
            try {
                when (val r = ubicacion.obtener()) {
                    is ResultadoUbicacion.Lista -> {
                        actualizar { copy(latitud = r.coordenadas.latitud, longitud = r.coordenadas.longitud, precision = r.coordenadas.precision) }
                        val p = r.coordenadas.precision
                        if (p != null && p > PRECISION_ACEPTABLE_M) {
                            avisar("Precisión baja (±${p.toInt()} m). Espera unos segundos a cielo abierto y vuelve a obtenerla.")
                        }
                    }
                    ResultadoUbicacion.SinPermiso ->
                        avisar("Sin permiso de ubicación. Actívalo en los ajustes de la app.", AccionAviso.AJUSTES_APP)
                    ResultadoUbicacion.Desactivada ->
                        avisar("La ubicación del teléfono está apagada.", AccionAviso.AJUSTES_UBICACION)
                    ResultadoUbicacion.SinSenal ->
                        avisar("No hubo señal GPS. Muévete a un lugar despejado e inténtalo de nuevo.")
                }
            } finally {
                _buscandoUbicacion.value = false
            }
        }
    }

    fun quitarUbicacion() = actualizar { copy(latitud = null, longitud = null, precision = null) }

    // ------------------------------------------------------------------ fotos

    /** Archivo donde la cámara debe dejar la foto; queda anotado por si Android cierra la app. */
    fun prepararCaptura(): File {
        val archivo = archivos.nuevaCaptura()
        actualizar { copy(capturaPendiente = archivo.path) }
        return archivo
    }

    fun capturaTerminada(ok: Boolean) {
        val ruta = estado.value.capturaPendiente ?: return
        actualizar { copy(capturaPendiente = null) }
        importarFoto {
            val captura = File(ruta)
            if (ok) archivos.importarCaptura(captura) else null.also { captura.delete() }
        }
    }

    fun fotoDeGaleria(uri: Uri) = importarFoto { archivos.importar { contenido.openInputStream(uri) } }

    private fun importarFoto(importar: () -> FotoBorrador?) {
        viewModelScope.launch {
            _procesandoFoto.value = true
            try {
                val foto = withContext(Dispatchers.IO) { runCatching(importar) }
                foto.onSuccess { f ->
                    if (f == null) return@onSuccess
                    if (estado.value.fotos.size >= MAX_FOTOS) {
                        withContext(Dispatchers.IO) { archivos.eliminar(listOf(f.archivo)) }
                        avisar("Ya hay $MAX_FOTOS fotos; quita una para agregar otra.")
                    } else {
                        actualizar { copy(fotos = fotos + f) }
                    }
                }.onFailure { avisar("No se pudo guardar la foto: ${it.message ?: "error desconocido"}") }
            } finally {
                _procesandoFoto.value = false
            }
        }
    }

    fun quitarFoto(foto: FotoBorrador) {
        actualizar { copy(fotos = fotos - foto) }
        // Si era nueva (no estaba guardada), el archivo ya no sirve.
        if (foto.archivo !in estado.value.fotosOriginales) {
            viewModelScope.launch(Dispatchers.IO) { archivos.eliminar(listOf(foto.archivo)) }
        }
    }

    // ------------------------------------------------------------------ guardar

    /** [otra]: guarda y deja el formulario listo para la siguiente del mismo frente. */
    fun guardar(otra: Boolean) {
        val actual = estado.value
        if (_guardando.value) return
        if (ValidadorExcavacion.validar(actual.borrador()).isNotEmpty()) {
            actualizar { copy(mostrarErrores = true) }
            return
        }
        viewModelScope.launch {
            _guardando.value = true
            try {
                val id = repo.guardar(actual.borrador())
                val numero = repo.borrador(id)?.numero
                preferencias.ultimoFrente = UltimoFrente(actual.sector.trim(), actual.lado, actual.sentido, actual.tipoTerrenoId)
                // Al editar una antigua (sin tipo) no se olvida el tipo que se viene usando.
                if (actual.nueva || actual.tipoExcavacionId != null) preferencias.ultimoTipoExcavacion = actual.tipoExcavacionId
                val quitadas = actual.fotosOriginales - actual.fotos.map { it.archivo }.toSet()
                if (quitadas.isNotEmpty()) withContext(Dispatchers.IO) { archivos.eliminar(quitadas) }

                val mensaje = "Excavación N° ${numero?.let(Formato::numero) ?: ""} guardada"
                if (otra) {
                    guardado[CLAVE] = nueva(actual.fecha)
                    avisar("$mensaje. Registra la siguiente.")
                } else {
                    _eventos.send(EventoFormulario.Terminado(mensaje))
                }
            } catch (e: ExcavacionInvalida) {
                erroresBase.value = e.errores
                guardado[CLAVE] = actual.copy(mostrarErrores = true)
                avisar(e.errores.values.first())
            } finally {
                _guardando.value = false
            }
        }
    }

    fun eliminar() {
        val id = estado.value.id
        if (id == 0L) return
        viewModelScope.launch {
            val archivosFotos = repo.eliminar(id)
            withContext(Dispatchers.IO) { archivos.eliminar(archivosFotos) }
            _eventos.send(EventoFormulario.Terminado("Excavación eliminada"))
        }
    }

    fun agregarTipoTerreno(nombre: String) {
        viewModelScope.launch {
            runCatching { repo.agregarTipoTerreno(nombre) }
                .onSuccess { id -> actualizar { copy(tipoTerrenoId = id) } }
                .onFailure { avisar(it.message ?: "No se pudo agregar el tipo") }
        }
    }

    private suspend fun avisar(texto: String, accion: AccionAviso? = null) = _eventos.send(EventoFormulario.Aviso(texto, accion))

    companion object {
        private const val CLAVE = "formulario"
        const val PRECISION_ACEPTABLE_M = 30f

        /**
         * Aplica un tipo de excavación: sus medidas reemplazan las escritas (una medida sin definir
         * queda vacía para escribirla); terreno y observaciones solo si el tipo los define.
         */
        fun EstadoFormulario.conTipo(t: TipoExcavacionEntity) = copy(
            tipoExcavacionId = t.id,
            largo = t.largo?.let(::decimalEditable).orEmpty(),
            ancho = t.ancho?.let(::decimalEditable).orEmpty(),
            profundidad = t.profundidad?.let(::decimalEditable).orEmpty(),
            tipoTerrenoId = t.tipoTerrenoId ?: tipoTerrenoId,
            observaciones = t.observaciones ?: observaciones,
        )

        /** 1.5 → "1,5"; 2.0 → "2" (como lo escribiría alguien). */
        fun decimalEditable(valor: Double): String =
            valor.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')
    }
}
