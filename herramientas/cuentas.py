"""Cuentas de Prepárate desde el PC, con el SDK de administración de Firebase.

Hace lo que la app no puede:
    crear-admin <usuario> <nombre>   crea una cuenta de admin
    cambiar-clave <usuario>          cambia la clave de cualquier cuenta (clave olvidada)

La clave se escribe sin que se vea en pantalla. Necesita la credencial de la
cuenta de servicio en herramientas/credencial-firebase.json (o en la ruta de la
variable PREPARATE_CREDENCIAL). Esa credencial controla TODAS las cuentas:
se queda solo en este PC, nunca va en git ni en el APK.

Instalación, una sola vez:
    python -m pip install firebase-admin
Uso:
    python herramientas/cuentas.py crear-admin papa "Papá"
    python herramientas/cuentas.py cambiar-clave ana
"""

import argparse
import getpass
import os
import re
import sys
from pathlib import Path

DOMINIO = "preparate.invalid"
USUARIO_VALIDO = re.compile(r"^[a-z0-9._-]{3,20}$")
CLAVE_MINIMA = 6
CREDENCIAL = Path(os.environ.get("PREPARATE_CREDENCIAL", Path(__file__).parent / "credencial-firebase.json"))


def iniciar():
    try:
        import firebase_admin
        from firebase_admin import credentials
    except ImportError:
        sys.exit("Falta firebase-admin. Instálalo con: python -m pip install firebase-admin")
    if not CREDENCIAL.is_file():
        sys.exit(
            f"No encuentro la credencial en {CREDENCIAL}.\n"
            "Descárgala en la consola de Firebase: Configuración del proyecto → Cuentas de servicio → "
            "Generar nueva clave privada."
        )
    firebase_admin.initialize_app(credentials.Certificate(str(CREDENCIAL)))


def usuario_valido(texto: str) -> str:
    usuario = texto.strip().lower()
    if not USUARIO_VALIDO.match(usuario):
        sys.exit("Usuario no válido: de 3 a 20 letras sin tilde, números, punto, guion o guion bajo.")
    return usuario


def pedir_clave() -> str:
    clave = getpass.getpass("Clave nueva: ")
    if len(clave) < CLAVE_MINIMA:
        sys.exit(f"La clave necesita al menos {CLAVE_MINIMA} caracteres.")
    if getpass.getpass("Repite la clave: ") != clave:
        sys.exit("Las claves no coinciden.")
    return clave


def crear_admin(usuario: str, nombre: str) -> None:
    from firebase_admin import auth, firestore

    clave = pedir_clave()
    try:
        cuenta = auth.create_user(email=f"{usuario}@{DOMINIO}", password=clave)
    except auth.EmailAlreadyExistsError:
        sys.exit(f"Ya existe una cuenta con el usuario {usuario}.")
    firestore.client().collection("usuarios").document(cuenta.uid).set(
        {"nombre": nombre.strip(), "usuario": usuario, "rol": "admin"}
    )
    print(f"Listo: {nombre} es admin. Entra en la app con el usuario {usuario}.")


def cambiar_clave(usuario: str) -> None:
    from firebase_admin import auth

    try:
        cuenta = auth.get_user_by_email(f"{usuario}@{DOMINIO}")
    except auth.UserNotFoundError:
        sys.exit(f"No existe ninguna cuenta con el usuario {usuario}.")
    auth.update_user(cuenta.uid, password=pedir_clave())
    print(f"Listo: {usuario} ya puede entrar con su clave nueva.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    comandos = parser.add_subparsers(dest="comando", required=True)
    admin = comandos.add_parser("crear-admin", help="crea una cuenta de admin")
    admin.add_argument("usuario")
    admin.add_argument("nombre")
    clave = comandos.add_parser("cambiar-clave", help="cambia la clave de una cuenta")
    clave.add_argument("usuario")
    args = parser.parse_args()

    usuario = usuario_valido(args.usuario)
    iniciar()
    if args.comando == "crear-admin":
        crear_admin(usuario, args.nombre)
    else:
        cambiar_clave(usuario)


if __name__ == "__main__":
    main()
