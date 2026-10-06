package cl.rutaitata.charlas.data

data class SeccionMarco(val titulo: String, val items: List<String>)

/** Contenido de la pestaña "Marco": rol, marco normativo y guía de uso de la charla. */
object Marco {

    const val ROL =
        "Este banco fue elaborado desde el rol de Experto en Prevención de Riesgos Senior, con experiencia en el sistema " +
            "de gestión de seguridad, salud ocupacional y medio ambiente (SSOMA) de Sacyr, para el proyecto \"Mejoramiento " +
            "de la Ruta Itata\": movimiento de tierras, pavimentación asfáltica, control y desvío de tránsito en ruta de alta " +
            "velocidad, operación de maquinaria pesada y clima variable de la zona sur."

    val secciones = listOf(
        SeccionMarco(
            "Marco normativo aplicado",
            listOf(
                "Ley 16.744 – Seguro contra accidentes del trabajo y enfermedades profesionales (Art. 68: obligación de " +
                    "implementar medidas de prevención; Art. 5: accidente de trayecto; Art. 76: denuncia de accidentes).",
                "Código del Trabajo – Art. 184 (deber de protección eficaz), Art. 184 bis (Ley 21.012: derecho a interrumpir " +
                    "labores ante riesgo grave e inminente) y Arts. 211-F a 211-J (manejo manual de carga, Ley 20.949).",
                "DS 594 – Condiciones sanitarias y ambientales básicas en los lugares de trabajo (Art. 37 factores de peligro, " +
                    "Art. 38 partes móviles, Arts. 44-52 incendios, Art. 53 EPP, Título IV agentes físicos y químicos, " +
                    "Arts. 109 a-c radiación UV).",
                "DS 44/2024 – Reglamento de gestión preventiva de los riesgos laborales, vigente desde el 1 de febrero de 2025. " +
                    "Reemplazó al DS 40 (y su \"Derecho a Saber\") y al DS 54: la obligación de informar los riesgos se " +
                    "mantiene y se refuerza.",
                "Ley 18.290 de Tránsito (con Ley 20.580 \"Tolerancia Cero\" y Ley 20.770 \"Ley Emilia\").",
                "Manual de Carreteras MOP, Vol. 6 (Seguridad Vial) y Manual de Señalización de Tránsito (MTT), Cap. 5: " +
                    "señalización transitoria y medidas de seguridad para trabajos en la vía.",
                "Normas y guías de apoyo: NCh 349 (excavaciones), NCh 1258 (protección contra caídas), NCh 2245 (Hojas de Datos " +
                    "de Seguridad), DS 18 (certificación de EPP), DS 63 (manejo manual de carga), Protocolos MINSAL " +
                    "(PREXOR, sílice/PLANESI, radiación UV).",
            ),
        ),
        SeccionMarco(
            "Cómo dictar la charla en 5 minutos",
            listOf(
                "Minuto 0 – Reúne a la cuadrilla en un lugar seguro, fuera de la calzada y lejos de equipos en movimiento. " +
                    "Que todos te vean y te escuchen.",
                "Minuto 1 – \"El por qué\": cuéntalo con tus palabras, mirando a la gente. Si tienes un caso real (sin nombres), úsalo.",
                "Minutos 2 a 4 – Puntos de control: no los leas, muéstralos. Señala la máquina, el cono, el talud. Pide a un " +
                    "trabajador que verifique cada punto en voz alta.",
                "Minuto 5 – Respaldo y pregunta de cierre: menciona la regla o norma en una frase y haz la pregunta. Si nadie " +
                    "responde bien, repasa el punto. Registra la asistencia.",
            ),
        ),
        SeccionMarco(
            "Distribución del mes",
            listOf(
                "Lunes: Maquinaria Pesada (inicio de semana, mayor movimiento de equipos).",
                "Martes: Control de Tránsito / Paleteros.",
                "Miércoles: Cuadrillas de Asfalto.",
                "Jueves: Obras de Arte / Manuales.",
                "Viernes: Riesgos Transversales / Clima.",
                "Sábado: refuerzo de una especialidad crítica (líneas eléctricas, conductores agresivos, gas licuado, trabajo en altura).",
            ),
        ),
        SeccionMarco(
            "Importante",
            listOf(
                "Las Reglas de Oro se citan en forma resumida como referencia. Antes de usar este banco, valídalo con la versión " +
                    "vigente de los estándares, procedimientos y matriz de riesgos del sistema SSOMA del proyecto.",
                "Cada charla debe quedar registrada con fecha, tema, relator y firma de los asistentes, según el procedimiento " +
                    "del proyecto.",
                "Distancias, alturas y criterios de detención que dependan del frente (zonas de exclusión, distancias a líneas " +
                    "eléctricas, visibilidad mínima) se toman del procedimiento de trabajo y del plan de desvío aprobado.",
            ),
        ),
    )
}
