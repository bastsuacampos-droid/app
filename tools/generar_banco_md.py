#!/usr/bin/env python3
"""Genera docs/BANCO_CHARLAS.md a partir de app/src/main/assets/charlas.json.

Uso: python3 tools/generar_banco_md.py
"""
import json
import re
import unicodedata
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"]

# Misma lógica que Recomendador.kt, para imprimir las charlas recomendadas por actividad.
PALABRAS_VACIAS = {
    "para", "por", "con", "los", "las", "del", "una", "uno", "unos", "unas", "que", "hoy", "voy",
    "vamos", "hacer", "haremos", "hare", "dia", "obra", "faena", "trabajo", "trabajos", "trabajar",
    "tarea", "tareas", "actividad", "cuadrilla", "esta", "este", "sobre", "entre", "desde", "como",
    "todo", "toda", "todos", "todas", "mas", "muy", "sin", "son", "ser", "hay",
}
RECOMENDADAS_POR_ACTIVIDAD = 6


def raices(texto: str) -> list[str]:
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", texto.lower()) if not unicodedata.combining(c)
    )
    resultado = []
    for palabra in re.split(r"[^a-zñ0-9]+", sin_tildes):
        if len(palabra) < 3 or palabra in PALABRAS_VACIAS:
            continue
        if palabra.endswith("es") and len(palabra) > 5:
            palabra = palabra[:-2]
        elif palabra.endswith("s") and len(palabra) > 4:
            palabra = palabra[:-1]
        resultado.append(palabra[:5])
    return resultado


def orden_codigo(c: dict) -> tuple[str, int]:
    """Como Charla.numero en Kotlin: OA-100 va después de OA-99, no de OA-10."""
    prefijo, _, numero = c["codigo"].rpartition("-")
    return prefijo, int(numero)


def recomendar(charlas: list[dict], actividad: dict) -> list[dict]:
    consulta = {r for p in actividad["palabras"] for r in raices(p)}
    preferidas = set(actividad.get("especialidades", []))
    puntuadas = []
    for c in charlas:
        titulo = set(raices(c["titulo"]))
        resto = set(raices(c["porQue"] + " " + " ".join(c["checklist"])))
        puntaje = sum(3 if r in titulo else 1 if r in resto else 0 for r in consulta)
        if puntaje > 0 and c["especialidad"] in preferidas:
            puntaje += 2
        if puntaje > 0:
            puntuadas.append((-puntaje, orden_codigo(c), c))
    return [c for _, _, c in sorted(puntuadas, key=lambda t: (t[0], t[1]))]


def main() -> None:
    datos = json.loads((RAIZ / "app/src/main/assets/charlas.json").read_text(encoding="utf-8"))
    esp = {e["id"]: e["nombre"] for e in datos["especialidades"]}
    por_id = {c["id"]: c for c in datos["charlas"]}

    o = [f"# Banco de Charlas de 5 Minutos – {datos['proyecto']}", ""]
    o.append("> Archivo generado desde `app/src/main/assets/charlas.json` con "
             "`python3 tools/generar_banco_md.py`. No editar a mano.")
    o += ["", f"**Versión {datos['version']}** · {datos.get('fecha', '')} — {datos.get('novedades', '')}", ""]

    o += ["## Índice por especialidad", ""]
    for eid, nombre in esp.items():
        propias = [c for c in datos["charlas"] if c["especialidad"] == eid]
        o += [f"### {nombre} ({len(propias)})", ""]
        o += [f"- **{c['codigo']}** {c['titulo']}" for c in propias]
        o.append("")

    if datos.get("actividades"):
        o += ["## Charlas recomendadas por actividad", ""]
        o.append("Las que la app propone primero en *¿Qué actividad harás?*.")
        o.append("")
        o.append("| Actividad | Charlas recomendadas |")
        o.append("|---|---|")
        for a in datos["actividades"]:
            codigos = ", ".join(c["codigo"] for c in recomendar(datos["charlas"], a)[:RECOMENDADAS_POR_ACTIVIDAD])
            o.append(f"| {a['nombre']} | {codigos} |")
        o.append("")

    o += ["## Plan sugerido (4 semanas, lunes a sábado)", ""]
    o.append("| Semana | Día | Código | Especialidad | Tema |")
    o.append("|---|---|---|---|---|")
    for e in sorted(datos.get("plan", []), key=lambda e: (e["semana"], e["dia"])):
        c = por_id[e["charla"]]
        o.append(f"| {e['semana']} | {DIAS[e['dia'] - 1]} | {c['codigo']} | {esp[c['especialidad']]} | {c['titulo']} |")
    o.append("")

    for eid, nombre in esp.items():
        o += [f"## {nombre}", ""]
        for c in (c for c in datos["charlas"] if c["especialidad"] == eid):
            o += [f"### {c['codigo']} · {c['titulo']}", "",
                  f"**El porqué (mensaje clave):** {c['porQue']}", "",
                  "**Puntos de control (checklist de terreno):**", ""]
            o += [f"{i}. [ ] {p}" for i, p in enumerate(c["checklist"], 1)]
            o += ["", "**Respaldo estándar:**", "",
                  f"- Regla de Oro: {c['reglaOro']}",
                  f"- Normativa: {c['normativa']}", "",
                  f"**Pregunta de cierre:** {c['preguntaCierre']}", ""]

    (RAIZ / "docs/BANCO_CHARLAS.md").write_text("\n".join(o), encoding="utf-8")


if __name__ == "__main__":
    main()
