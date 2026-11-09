"""Paquete de contenido PROVISIONAL de la fase 1: un solo tema de Matemática.

En la fase 2 lo reemplaza `exportar-paquete` del proyecto web. Se ejecuta con el
Python del proyecto web, porque usa su generador de variantes:

    ../repaso-admisiones/.venv/Scripts/python.exe herramientas/exportar_prueba.py
"""

import json
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path

AQUI = Path(__file__).resolve().parent
WEB = AQUI.parent.parent / "repaso-admisiones"
DESTINO = AQUI.parent / "app" / "src" / "main" / "assets" / "contenido"
LECCION = "mat.fracciones.suma-igual-denominador"
SEMILLAS = 300
MAXIMO_VARIANTES = 30

sys.path.insert(0, str(WEB))
from app.content.loader import cargar_contenido  # noqa: E402
from app.generator.expresiones import formatear  # noqa: E402
from app.generator.variantes import generar_variante  # noqa: E402
from app.generator.verificador import verificar  # noqa: E402

REFERENCIA = AQUI.parent / "dominio" / "src" / "test" / "resources" / "referencia-verificador.json"


def entradas_de_prueba(v) -> list[str]:
    """Respuestas típicas de un estudiante para comparar Kotlin con Python."""
    num, den = (v.respuesta_texto.split("/") + ["1"])[:2]
    n, d = int(num), int(den)
    return [
        v.respuesta_texto, f" {n} / {d} ", f"{n * 2}/{d * 2}", f"{n * 3}/{d * 3}", f"-{n}/{d}",
        f"{n / d:.2f}".replace(".", ","), f"{n / d}", f"{n}/0", f"{n}", "abc", "", "1 1/2", "3/2",
        *(str(formatear(e.valor)) for e in v.errores),
    ]


def escribir(ruta: Path, datos: object) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=1), encoding="utf-8")


def main() -> None:
    contenido = cargar_contenido(WEB / "content", publicar_borradores=True)
    encontrado = contenido.buscar_leccion(LECCION)
    if encontrado is None:
        sys.exit(f"no existe la lección {LECCION}")
    materia, seccion, leccion = encontrado

    casos = []
    for ejercicio in leccion.ejercicios:
        _, plantilla = contenido.buscar_plantilla(ejercicio.plantilla)
        variantes, vistos = [], set()
        for semilla in range(SEMILLAS):
            v = generar_variante(plantilla, materia.bancos, semilla)
            if v.enunciado in vistos:
                continue
            vistos.add(v.enunciado)
            for entrada in entradas_de_prueba(v):
                r = verificar(v, entrada)
                casos.append({"plantilla": plantilla.id, "semilla": semilla, "entrada": entrada,
                              "estado": r.estado.value, "error_comun": r.error_comun})
            variantes.append({
                "semilla": semilla,
                "enunciado": v.enunciado,
                "respuesta": v.respuesta_texto,
                "aceptadas": list(v.aceptadas),
                "opciones": v.opciones,
                "pistas": v.pistas,
                "solucion": v.solucion,
                "errores": [{"valor": formatear(e.valor), "explicacion": e.explicacion} for e in v.errores],
            })
            if len(variantes) == MAXIMO_VARIANTES:
                break
        escribir(DESTINO / "variantes" / f"{plantilla.id}.json", {
            "id": plantilla.id,
            "tipo": plantilla.tipo.value,
            "verificador": plantilla.verificador.value,
            "forma_irreducible": plantilla.forma_irreducible,
            "ignorar_tildes": plantilla.ignorar_tildes,
            "variantes": variantes,
        })
        print(f"{plantilla.id}: {len(variantes)} variantes")

    escribir(REFERENCIA, casos)
    print(f"{len(casos)} casos de referencia del verificador")
    escribir(DESTINO / "lecciones" / f"{leccion.id}.json", {
        "id": leccion.id,
        "materia": materia.materia.id,
        "seccion": seccion.ref.id,
        "titulo": leccion.titulo,
        "concepto": leccion.concepto,
        "contenido": leccion.contenido,
        "ejercicios": [{"plantilla": e.plantilla, "cantidad": e.cantidad} for e in leccion.ejercicios],
    })
    commit = subprocess.run(["git", "-C", str(WEB), "rev-parse", "--short", "HEAD"],
                            capture_output=True, text=True, check=True).stdout.strip()
    escribir(DESTINO / "manifiesto.json", {
        "version": 1,
        "fecha": datetime.now(UTC).isoformat(timespec="seconds"),
        "commit": commit,
        "nota": "Paquete provisional de la fase 1",
        "lecciones": [leccion.id],
    })


if __name__ == "__main__":
    main()
