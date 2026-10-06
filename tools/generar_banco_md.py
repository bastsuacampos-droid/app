#!/usr/bin/env python3
"""Genera docs/BANCO_CHARLAS.md a partir de app/src/main/assets/charlas.json.

Uso: python3 tools/generar_banco_md.py
"""
import json
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"]


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
