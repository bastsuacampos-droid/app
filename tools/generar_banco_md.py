#!/usr/bin/env python3
"""Genera docs/BANCO_CHARLAS.md a partir de app/src/main/assets/charlas.json.

Uso: python3 tools/generar_banco_md.py
"""
import json
from collections import defaultdict
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"]


def main() -> None:
    datos = json.loads((RAIZ / "app/src/main/assets/charlas.json").read_text(encoding="utf-8"))
    esp = {e["id"]: e["nombre"] for e in datos["especialidades"]}
    charlas = sorted(datos["charlas"], key=lambda c: (c["semana"], c["dia"]))

    o = [f"# Banco Mensual de Charlas de 5 Minutos – {datos['proyecto']}", ""]
    o.append("> Archivo generado desde `app/src/main/assets/charlas.json` con "
             "`python3 tools/generar_banco_md.py`. No editar a mano.")
    o += ["", f"**Versión {datos['version']}** · {datos.get('fecha', '')} — {datos.get('novedades', '')}"]
    o += ["", "## Planificación (4 semanas, lunes a sábado)", ""]
    o.append("| Semana | Día | Especialidad | Tema |")
    o.append("|---|---|---|---|")
    for c in charlas:
        o.append(f"| {c['semana']} | {DIAS[c['dia'] - 1]} | {esp[c['especialidad']]} | {c['titulo']} |")

    o += ["", "## Temas por especialidad", ""]
    por_esp = defaultdict(list)
    for c in charlas:
        por_esp[c["especialidad"]].append(c)
    for eid, nombre in esp.items():
        o.append(f"**{nombre}** ({len(por_esp[eid])})")
        o += [f"- S{c['semana']} {DIAS[c['dia'] - 1]}: {c['titulo']}" for c in por_esp[eid]]
        o.append("")

    semana_actual = None
    for c in charlas:
        if c["semana"] != semana_actual:
            semana_actual = c["semana"]
            o += [f"## Semana {semana_actual}", ""]
        o += [f"### {DIAS[c['dia'] - 1]} · {c['titulo']}", "",
              f"*Especialidad: {esp[c['especialidad']]}*", "",
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
