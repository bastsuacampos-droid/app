package cl.sacyr.rutaitata.charlas.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.charlas.data.Banco
import cl.sacyr.rutaitata.charlas.data.Calendario
import cl.sacyr.rutaitata.charlas.data.Charla
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.data.SEMANAS_CICLO
import cl.sacyr.rutaitata.charlas.data.comoTexto
import cl.sacyr.rutaitata.charlas.data.nombreDia
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val RUTA_LISTA = "lista"
private const val RUTA_ACERCA = "acerca"
private const val PREFIJO_DETALLE = "detalle/"

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun AppCharlas(banco: Banco, progreso: Progreso) {
    var ruta by rememberSaveable { mutableStateOf(RUTA_LISTA) }
    BackHandler(enabled = ruta != RUTA_LISTA) { ruta = RUTA_LISTA }

    val densidad = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(densidad.density, densidad.fontScale * progreso.escalaTexto),
    ) {
        val detalle = ruta.removePrefix(PREFIJO_DETALLE).toIntOrNull()?.let(banco::charla)
        when {
            ruta == RUTA_ACERCA -> PantallaAcerca(banco, progreso, onVolver = { ruta = RUTA_LISTA })
            detalle != null -> PantallaDetalle(banco, detalle, progreso, onVolver = { ruta = RUTA_LISTA })
            else -> PantallaLista(
                banco = banco,
                progreso = progreso,
                onAbrir = { ruta = PREFIJO_DETALLE + it.id },
                onAcerca = { ruta = RUTA_ACERCA },
            )
        }
    }
}

// ---------------------------------------------------------------- Lista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaLista(
    banco: Banco,
    progreso: Progreso,
    onAbrir: (Charla) -> Unit,
    onAcerca: () -> Unit,
) {
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val hoy = LocalDate.now()
    val diaCiclo = Calendario.diaCiclo(progreso.inicioCiclo, hoy)
    val charlaHoy = banco.charla(diaCiclo.semana, diaCiclo.dia)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Charlas de 5 minutos", fontWeight = FontWeight.Bold)
                        Text(banco.proyecto, style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onAcerca) {
                        Icon(Icons.Filled.Info, contentDescription = "Ciclo y normativa")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        val visibles = banco.charlas.filter { filtro == null || it.especialidadId == filtro }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (charlaHoy != null) {
                item {
                    TarjetaHoy(
                        banco = banco,
                        charla = charlaHoy,
                        esDomingo = hoy.dayOfWeek == DayOfWeek.SUNDAY,
                        realizada = progreso.realizada(charlaHoy.id) != null,
                        onAbrir = { onAbrir(charlaHoy) },
                    )
                }
            }
            item { ResumenAvance(banco, progreso) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todas") })
                    banco.especialidades.forEach { esp ->
                        FilterChip(
                            selected = filtro == esp.id,
                            onClick = { filtro = if (filtro == esp.id) null else esp.id },
                            label = { Text(esp.nombre) },
                            leadingIcon = { Punto(colorDe(esp.colorHex)) },
                        )
                    }
                }
            }
            visibles.groupBy { it.semana }.forEach { (semana, charlas) ->
                item(key = "semana$semana") {
                    Text(
                        text = "Semana $semana · ${rangoSemana(progreso.inicioCiclo, semana)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(charlas, key = { it.id }) { charla ->
                    FilaCharla(
                        banco = banco,
                        charla = charla,
                        esHoy = charla == charlaHoy,
                        realizada = progreso.realizada(charla.id) != null,
                        onClick = { onAbrir(charla) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaHoy(
    banco: Banco,
    charla: Charla,
    esDomingo: Boolean,
    realizada: Boolean,
    onAbrir: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAbrir),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (esDomingo) "PREPARA LA CHARLA DEL LUNES" else "CHARLA DE HOY · 08:00",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(charla.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Semana ${charla.semana} · ${nombreDia(charla.dia)} · ${banco.especialidad(charla.especialidadId).nombre}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (realizada) {
                Spacer(Modifier.height(6.dp))
                Text("✔ Ya realizada", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResumenAvance(banco: Banco, progreso: Progreso) {
    val total = banco.charlas.size
    val hechas = progreso.totalRealizadas
    Column(Modifier.padding(top = 4.dp)) {
        Text(
            "Avance del ciclo: $hechas de $total charlas realizadas",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else hechas.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun FilaCharla(
    banco: Banco,
    charla: Charla,
    esHoy: Boolean,
    realizada: Boolean,
    onClick: () -> Unit,
) {
    val esp = banco.especialidad(charla.especialidadId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (esHoy) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colorDe(esp.colorHex), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    nombreDia(charla.dia).take(3).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    charla.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(esp.nombre, style = MaterialTheme.typography.labelMedium)
            }
            if (realizada) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Realizada",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Detalle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaDetalle(
    banco: Banco,
    charla: Charla,
    progreso: Progreso,
    onVolver: () -> Unit,
) {
    val context = LocalContext.current
    val esp = banco.especialidad(charla.especialidadId)
    val verificados = progreso.verificados(charla.id)
    val realizada = progreso.realizada(charla.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Semana ${charla.semana} · ${nombreDia(charla.dia)}") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { progreso.cambiarEscala(-0.1f) }) {
                        Text("A−", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = { progreso.cambiarEscala(0.1f) }) {
                        Text("A+", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, charla.titulo)
                            putExtra(Intent.EXTRA_TEXT, charla.comoTexto(banco))
                        }
                        context.startActivity(Intent.createChooser(envio, "Compartir charla"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Punto(colorDe(esp.colorHex))
                Spacer(Modifier.width(8.dp))
                Text(esp.nombre.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(charla.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Seccion(
                titulo = "El porqué · mensaje clave",
                fondo = MaterialTheme.colorScheme.secondaryContainer,
                contenido = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(charla.porQue, style = MaterialTheme.typography.bodyLarge)
            }

            Seccion(titulo = "Puntos de control · checklist de terreno (${verificados.size}/${charla.checklist.size})") {
                charla.checklist.forEachIndexed { i, punto ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { progreso.alternarPunto(charla.id, i) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = i in verificados,
                            onCheckedChange = { progreso.alternarPunto(charla.id, i) },
                        )
                        Text(
                            "${i + 1}. $punto",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            Seccion(titulo = "Respaldo estándar") {
                Text("Regla de Oro", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(charla.reglaOro, style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Normativa aplicable", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(charla.normativa, style = MaterialTheme.typography.bodyMedium)
            }

            Seccion(titulo = "Pregunta de cierre a la cuadrilla") {
                Text("“${charla.preguntaCierre}”", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }

            if (realizada == null) {
                val completa = verificados.size == charla.checklist.size
                Button(
                    onClick = { progreso.marcarRealizada(charla.id, LocalDate.now()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (completa) "Marcar charla como realizada" else "Marcar como realizada (checklist incompleto)")
                }
            } else {
                Text(
                    "✔ Charla realizada el ${realizada.format(FORMATO_FECHA)}",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { progreso.marcarRealizada(charla.id, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Deshacer")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Acerca

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaAcerca(banco: Banco, progreso: Progreso, onVolver: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ciclo y normativa") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Seccion(titulo = "Ciclo actual") {
                Text(
                    "El banco cubre $SEMANAS_CICLO semanas de lunes a sábado (${banco.charlas.size} charlas). " +
                        "La semana 1 comenzó el lunes ${progreso.inicioCiclo.format(FORMATO_FECHA)}; " +
                        "al terminar la semana 4 el ciclo vuelve a la semana 1.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Iniciar nuevo ciclo esta semana")
                }
            }

            Seccion(titulo = "Cómo usar cada charla (5 minutos)") {
                Text(
                    "1. Lee el porqué con tus palabras, mirando a la cuadrilla (1 min).\n" +
                        "2. Recorre los 3 puntos de control y verifícalos en terreno antes de partir (2 min).\n" +
                        "3. Cierra con la Regla de Oro y la normativa que la respalda (1 min).\n" +
                        "4. Haz la pregunta de cierre y escucha la respuesta (1 min).\n" +
                        "5. Registra la asistencia en el formato oficial del proyecto.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Seccion(titulo = "Especialidades") {
                banco.especialidades.forEach { esp ->
                    val cantidad = banco.charlas.count { it.especialidadId == esp.id }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Punto(colorDe(esp.colorHex))
                        Spacer(Modifier.width(8.dp))
                        Text("${esp.nombre} ($cantidad)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Seccion(titulo = "Marco normativo de referencia") {
                Text(
                    "• Ley 16.744 – Seguro social contra accidentes del trabajo y enfermedades profesionales.\n" +
                        "• Código del Trabajo Arts. 184 y 184 bis (Ley 21.012) – deber de protección y derecho a interrumpir labores.\n" +
                        "• DS 594 – Condiciones sanitarias y ambientales básicas en los lugares de trabajo.\n" +
                        "• DS 40 – reemplazado por el DS 44/2024 (vigente desde febrero de 2025): gestión preventiva e información de riesgos.\n" +
                        "• Ley 18.290 de Tránsito, Ley 20.580 (Tolerancia Cero) y Ley 20.770 (Ley Emilia).\n" +
                        "• Manual de Carreteras MOP, Vol. 6 (Seguridad Vial) y Manual de Señalización de Tránsito, Cap. 5.\n" +
                        "• Normas NCh 349, 351, 1258, 2245, 2501 y protocolos MINSAL (PREXOR, PLANESI, radiación UV).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Seccion(
                titulo = "Importante",
                fondo = MaterialTheme.colorScheme.secondaryContainer,
                contenido = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    "Esta app es una guía de apoyo para capataces y supervisores. Las Reglas de Oro se presentan " +
                        "como principios; valida su redacción oficial y los valores del proyecto (distancias, velocidades, " +
                        "relevos) con el Departamento de Prevención de Riesgos y el sistema de gestión SSOMA vigente. " +
                        "No reemplaza los procedimientos de trabajo seguro, el plan de desvío aprobado ni el registro oficial de asistencia.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Iniciar un nuevo ciclo?") },
            text = { Text("La semana actual pasará a ser la semana 1 y se borrará el avance registrado (charlas realizadas y checklists).") },
            confirmButton = {
                TextButton(onClick = {
                    progreso.reiniciarCiclo(LocalDate.now())
                    confirmar = false
                }) { Text("Iniciar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            },
        )
    }
}

// ---------------------------------------------------------------- Comunes

@Composable
private fun Seccion(
    titulo: String,
    fondo: Color = MaterialTheme.colorScheme.surfaceVariant,
    contenido: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    cuerpo: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = contenido),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            cuerpo()
        }
    }
}

@Composable
private fun Punto(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .background(color, CircleShape),
    )
}

private fun rangoSemana(inicioCiclo: LocalDate, semana: Int): String {
    val hoy = LocalDate.now()
    val actual = Calendario.diaCiclo(inicioCiclo, hoy).semana
    // Lunes de la próxima ocurrencia (o la actual) de esa semana del ciclo.
    val lunesActual = Calendario.lunesDe(if (hoy.dayOfWeek == DayOfWeek.SUNDAY) hoy.plusDays(1) else hoy)
    val desfase = Math.floorMod(semana - actual, SEMANAS_CICLO)
    val lunes = lunesActual.plusWeeks(desfase.toLong())
    val formato = DateTimeFormatter.ofPattern("dd/MM")
    return "${lunes.format(formato)} al ${lunes.plusDays(5).format(formato)}"
}
