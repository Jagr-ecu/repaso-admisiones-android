"""Traspaso del progreso de la web (data/app.db) a la cuenta Android de un estudiante (fase 7).

Por defecto NO escribe nada: muestra lo que subiría y lo que no se puede traspasar. Con --subir,
primero respalda app.db, luego escribe en Firestore con el mismo formato que la app
(sincro/Documentos.kt) y al final vuelve a leer lo subido para comprobar que coincide.

    python herramientas/traspasar.py --usuario ana
    python herramientas/traspasar.py --usuario ana --subir

Cómo se traspasa:
- Los ids pasan de números a UUID fijos (uuid5): volver a correrlo no duplica nada.
- Todo va al curso Admisiones. El usuario de destino sale de la ruta, como en la app.
- `memoria` no se sube: la app la recalcula desde los intentos al bajar el historial.
- `mensajes_tutor` no se sube: las conversaciones con Kiwi no se guardan (PLAN, sección 6).
- Tampoco pasan la justificación, la confianza ni los segundos: la app no los tiene.
- La web elegía semillas al azar y el paquete de la app trae un conjunto fijo. Si el ejercicio
  está en el paquete (misma huella), se usa su semilla; si no, se deja la semilla original. Eso
  basta para la historia, los puntos, la memoria y el panel, pero la app no puede volver a
  mostrar ese ejercicio. Por eso:
  * Un tema o repaso que quedó a medias se cierra sin completar (como "Volver a empezar") y se
    traspasan solo sus ejercicios respondidos.
  * Una prueba o examen a medias no se traspasa.
  * Si una prueba o examen cerrado tiene ejercicios que no están en el paquete, su pantalla de
    resultado no se podría abrir: el script se detiene, salvo con --forzar.

Necesita firebase-admin y la credencial de herramientas/credencial-firebase.json (ver cuentas.py).
"""

import argparse
import json
import sqlite3
import sys
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PAQUETE = RAIZ / "app" / "src" / "main" / "assets" / "contenido"
BASE_WEB = RAIZ.parent / "repaso-admisiones" / "data" / "app.db"
CURSO = "admisiones"
DOMINIO = "preparate.invalid"
FORMATO = 1  # el de sincro/Documentos.kt
ESPACIO = uuid.UUID("6f1c2a52-6b8e-4c36-9b43-2d2a8f0e7c11")  # para los uuid5 del traspaso
PRACTICA = {"leccion", "repaso"}
LOTE = 400


def fecha(texto):
    if texto is None:
        return None
    d = datetime.fromisoformat(texto)
    return d if d.tzinfo else d.replace(tzinfo=timezone.utc)


def nuevo_id(tabla, viejo):
    return str(uuid.uuid5(ESPACIO, f"web:{tabla}:{viejo}"))


def sin_vacios(campos):
    return {k: v for k, v in campos.items() if v is not None}


# --- Leer ---------------------------------------------------------------------------------

def leer_web(ruta: Path, usuario_web: int | None):
    conn = sqlite3.connect(f"file:{ruta}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    estudiantes = [r["id"] for r in conn.execute("SELECT id FROM usuarios WHERE rol = 'estudiante'")]
    if usuario_web is None:
        if len(estudiantes) != 1:
            sys.exit(f"La web tiene {len(estudiantes)} estudiantes ({estudiantes}): elige uno con --web-usuario.")
        usuario_web = estudiantes[0]
    elif usuario_web not in estudiantes:
        sys.exit(f"El usuario {usuario_web} de la web no es un estudiante.")
    filas = lambda sql: [dict(r) for r in conn.execute(sql, (usuario_web,))]
    datos = {
        "sesiones": filas("SELECT * FROM sesiones WHERE usuario_id = ? ORDER BY id"),
        "intentos": filas("SELECT * FROM intentos WHERE usuario_id = ? ORDER BY id"),
        "puntos": filas("SELECT * FROM puntos WHERE usuario_id = ? ORDER BY id"),
        "mensajes": conn.execute("SELECT COUNT(*) FROM mensajes_tutor WHERE usuario_id = ?", (usuario_web,)).fetchone()[0],
    }
    conn.close()
    return usuario_web, datos


def leer_paquete():
    cursos = json.loads((PAQUETE / "cursos.json").read_text(encoding="utf-8"))["cursos"]
    curso = next(c for c in cursos if c["id"] == CURSO)
    huellas = {}

    def semillas(plantilla_id):
        if plantilla_id not in huellas:
            ruta = PAQUETE / CURSO / "variantes" / f"{plantilla_id}.json"
            if not ruta.is_file():
                huellas[plantilla_id] = None
            else:
                datos = json.loads(ruta.read_text(encoding="utf-8"))
                huellas[plantilla_id] = {v["huella"]: v["semilla"] for v in datos["variantes"]}
        return huellas[plantilla_id]

    return curso, semillas


def respaldar(ruta: Path) -> Path:
    destino = ruta.parent / "respaldos" / f"app-antes-de-traspasar-{datetime.now():%Y%m%d-%H%M%S}.db"
    destino.parent.mkdir(exist_ok=True)
    origen = sqlite3.connect(f"file:{ruta}?mode=ro", uri=True)
    copia = sqlite3.connect(destino)
    origen.backup(copia)  # incluye lo que esté en el WAL
    copia.close()
    origen.close()
    return destino


# --- Armar los documentos -----------------------------------------------------------------

def armar(datos, curso, semillas, servidor):
    """Los documentos para Firestore y un informe de lo que no se pudo traspasar tal cual."""
    materias = {m["id"] for m in curso["materias"]}
    secciones = {s["id"] for m in curso["materias"] for s in m["secciones"]}
    lecciones = {l["id"] for m in curso["materias"] for s in m["secciones"] for l in s["lecciones"]}
    informe = Counter()
    intentos_por_sesion = defaultdict(list)
    for i in datos["intentos"]:
        intentos_por_sesion[i["sesion_id"]].append(i)

    sesiones, intentos, puntos, problemas = {}, {}, {}, []
    ids_intento = {i["id"]: nuevo_id("intento", i["id"]) for i in datos["intentos"]}

    for s in datos["sesiones"]:
        if s["materia"] not in materias:
            problemas.append(f"sesión {s['id']}: la materia '{s['materia']}' no está en el paquete")
            continue
        if s["leccion_id"] and s["leccion_id"] not in lecciones:
            problemas.append(f"sesión {s['id']}: el tema '{s['leccion_id']}' no está publicado en el paquete")
        if s["seccion"] and s["seccion"] not in secciones:
            problemas.append(f"sesión {s['id']}: la sección '{s['seccion']}' no está en el paquete")
        suyos = sorted(intentos_por_sesion[s["id"]], key=lambda i: (i["mostrado"], i["id"]))
        fin, aprobada, nota = s["fin"], s["aprobada"], s["nota"]
        abierta = fin is None
        if abierta and s["tipo"] not in PRACTICA:
            informe["pruebas a medias (no pasan)"] += 1
            continue
        if abierta:
            # Tema o repaso a medias: se cierra sin completar, como "Volver a empezar".
            suyos = [i for i in suyos if i["respondido"] is not None]
            ultima = max([s["inicio"]] + [i["respondido"] for i in suyos])
            fin, aprobada = ultima, 0
            informe["temas a medias (se cierran sin completar)"] += 1

        sid = nuevo_id("sesion", s["id"])
        conceptos = json.loads(s["conceptos"]) if s["conceptos"] else []
        sesiones[sid] = sin_vacios({
            "curso_id": CURSO, "materia_id": s["materia"], "tipo": s["tipo"],
            "seccion_id": s["seccion"], "leccion_id": s["leccion_id"], "conceptos": conceptos,
            "inicio": fecha(s["inicio"]), "fin": fecha(fin), "nota": nota,
            "aprobada": None if aprobada is None else bool(aprobada),
            "formato": FORMATO, "actualizado": servidor,
        })

        for orden, i in enumerate(suyos):
            tabla = semillas(i["plantilla_id"])
            if tabla is None:
                problemas.append(f"intento {i['id']}: la plantilla '{i['plantilla_id']}' ya no existe")
                continue
            semilla = tabla.get(i["huella"])
            if semilla is None:
                semilla = i["semilla"]
                informe["ejercicios que no están en el paquete"] += 1
                if s["tipo"] not in PRACTICA:
                    problemas.append(
                        f"intento {i['id']} ({i['plantilla_id']}) de una {s['tipo']}: su resultado no se podría abrir"
                    )
            else:
                informe["ejercicios encontrados por huella"] += 1
            intentos[ids_intento[i["id"]]] = sin_vacios({
                "curso_id": CURSO, "materia_id": i["materia"], "sesion_id": sid, "orden": orden,
                "plantilla_id": i["plantilla_id"], "concepto": i["concepto"],
                "plantilla_version": i["plantilla_version"], "semilla": semilla, "huella": i["huella"],
                "mostrado": fecha(i["mostrado"]),
                "repite": ids_intento.get(i["repite"]) if i["repite"] is not None else None,
                "respondido": fecha(i["respondido"]), "respuesta": i["respuesta"], "estado": i["estado"],
                "correcta": None if i["correcta"] is None else bool(i["correcta"]),
                "error_comun": i["error_comun"], "pistas_usadas": i["pistas_usadas"] or 0,
                "puntaje": i["puntaje"], "revision": i["revision"],
                "formato": FORMATO, "actualizado": servidor,
            })

    for p in datos["puntos"]:
        doc = f"{CURSO}~{p['motivo']}~{p['referencia']}"
        puntos[doc] = {
            "curso_id": CURSO, "motivo": p["motivo"], "referencia": p["referencia"], "cantidad": p["cantidad"],
            "creado": fecha(p["creado"]), "formato": FORMATO, "actualizado": servidor,
        }
    return {"sesiones": sesiones, "intentos": intentos, "puntos": puntos}, informe, problemas


def resumen(docs):
    """Lo que se compara antes y después: temas completados, notas y puntos."""
    sesiones = docs["sesiones"].values()
    temas = sorted({s["leccion_id"] for s in sesiones if s["tipo"] == "leccion" and s.get("aprobada")})
    notas = sorted((s["tipo"], s.get("seccion_id") or "", s.get("nota")) for s in sesiones
                   if s["tipo"] not in PRACTICA and s.get("fin") is not None)
    return {
        "sesiones": len(docs["sesiones"]),
        "intentos": len(docs["intentos"]),
        "respondidos": sum(1 for i in docs["intentos"].values() if i.get("respondido") is not None),
        "temas completados": temas,
        "notas": notas,
        "puntos": sum(p["cantidad"] for p in docs["puntos"].values()),
    }


# --- Firestore ----------------------------------------------------------------------------

def uid_de(usuario):
    from firebase_admin import auth, firestore
    try:
        u = auth.get_user_by_email(f"{usuario}@{DOMINIO}")
    except auth.UserNotFoundError:
        sys.exit(f"No existe la cuenta '{usuario}'. Créala primero desde el panel de admin de la app.")
    perfil = firestore.client().collection("usuarios").document(u.uid).get()
    if not perfil.exists or perfil.get("rol") != "estudiante":
        sys.exit(f"La cuenta '{usuario}' no tiene el rol estudiante.")
    return u.uid


def subir(uid, docs):
    from firebase_admin import firestore
    db = firestore.client()
    base = db.collection("estudiantes").document(uid)
    pendientes = [(c, i, d) for c, grupo in docs.items() for i, d in grupo.items()]
    for inicio in range(0, len(pendientes), LOTE):
        lote = db.batch()
        for coleccion, doc_id, datos in pendientes[inicio:inicio + LOTE]:
            lote.set(base.collection(coleccion).document(doc_id), datos, merge=True)
        lote.commit()
    return len(pendientes)


def leer_subido(uid, docs):
    from firebase_admin import firestore
    base = firestore.client().collection("estudiantes").document(uid)
    leidos = {}
    for coleccion, grupo in docs.items():
        refs = [base.collection(coleccion).document(i) for i in grupo]
        leidos[coleccion] = {d.id: d.to_dict() for d in firestore.client().get_all(refs) if d.exists}
    return leidos


# --- Principal ----------------------------------------------------------------------------

def main():
    sys.stdout.reconfigure(encoding="utf-8")  # la consola de Windows no siempre usa UTF-8
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--usuario", required=True, help="usuario Android del estudiante (debe existir)")
    ap.add_argument("--web-usuario", type=int, help="id del estudiante en la web (si hay más de uno)")
    ap.add_argument("--base", type=Path, default=BASE_WEB, help=f"base de la web (por defecto {BASE_WEB})")
    ap.add_argument("--subir", action="store_true", help="escribir de verdad en Firestore")
    ap.add_argument("--forzar", action="store_true", help="subir aunque haya pruebas con ejercicios fuera del paquete")
    args = ap.parse_args()

    if not args.base.is_file():
        sys.exit(f"No encuentro la base de la web en {args.base}")
    usuario_web, datos = leer_web(args.base, args.web_usuario)
    curso, semillas = leer_paquete()

    from cuentas import iniciar, usuario_valido  # misma credencial y mismas reglas de usuario
    usuario = usuario_valido(args.usuario)
    iniciar()
    from firebase_admin import firestore
    uid = uid_de(usuario)

    docs, informe, problemas = armar(datos, curso, semillas, firestore.SERVER_TIMESTAMP)
    antes = resumen(docs)
    print(f"Web: estudiante {usuario_web}  →  Android: {usuario} ({uid})")
    print(f"  sesiones {antes['sesiones']}, intentos {antes['intentos']} ({antes['respondidos']} respondidos), "
          f"puntos {antes['puntos']}")
    print(f"  temas completados ({len(antes['temas completados'])}): {', '.join(antes['temas completados']) or '—'}")
    print(f"  pruebas y exámenes: {antes['notas'] or '—'}")
    for k, v in sorted(informe.items()):
        print(f"  {k}: {v}")
    print(f"  conversaciones con Kiwi que NO se traspasan: {datos['mensajes']}")
    for p in problemas:
        print(f"  ! {p}")

    if not args.subir:
        print("\nNo se escribió nada. Para traspasar de verdad, vuelve a correrlo con --subir.")
        return
    if problemas and not args.forzar:
        sys.exit("\nHay problemas (marcados con !). Revísalos; si aun así quieres subir, usa --forzar.")

    copia = respaldar(args.base)
    print(f"\nRespaldo de la web: {copia}")
    escritos = subir(uid, docs)
    print(f"Subidos {escritos} documentos.")

    despues = resumen(leer_subido(uid, docs))
    for k in antes:
        estado = "ok" if antes[k] == despues[k] else "NO COINCIDE"
        print(f"  {k}: {estado}")
    if antes != despues:
        sys.exit("La comprobación falló: revisa la consola de Firebase antes de seguir.")
    print("Todo coincide. El estudiante verá su progreso al entrar en la app (se baja solo).")


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    main()
