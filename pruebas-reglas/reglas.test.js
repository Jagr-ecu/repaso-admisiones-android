// Pruebas de ../firestore.rules con el emulador de Firestore (PLAN-MIGRACION.md, sección 4).
// Se corren con `npm test` desde esta carpeta. Ver README.md.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'mocha';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, getDocs, collection, query, where, setDoc, serverTimestamp } from 'firebase/firestore';

let entorno;

const ANA = 'uid-ana';
const LEO = 'uid-leo';
const ADMIN = 'uid-admin';
const EXTERNO = 'uid-externo'; // tiene cuenta en Auth, pero no perfil en usuarios/

const sesion = { curso_id: 'admisiones', materia_id: 'matematica', tipo: 'leccion', formato: 1 };

before(async () => {
  entorno = await initializeTestEnvironment({
    projectId: 'demo-preparate',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 },
  });
});

after(async () => {
  await entorno.cleanup();
});

beforeEach(async () => {
  await entorno.clearFirestore();
  await entorno.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'usuarios', ANA), { nombre: 'Ana', usuario: 'ana', rol: 'estudiante' });
    await setDoc(doc(db, 'usuarios', LEO), { nombre: 'Leo', usuario: 'leo', rol: 'estudiante' });
    await setDoc(doc(db, 'usuarios', ADMIN), { nombre: 'Papá', usuario: 'papa', rol: 'admin' });
    await setDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1'), sesion);
    await setDoc(doc(db, 'estudiantes', LEO, 'sesiones', 's2'), sesion);
  });
});

const como = (uid) => entorno.authenticatedContext(uid).firestore();

describe('estudiante', () => {
  it('lee y escribe su propio progreso', async () => {
    const db = como(ANA);
    await assertSucceeds(getDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1')));
    for (const c of ['sesiones', 'intentos', 'puntos', 'resumen']) {
      await assertSucceeds(setDoc(doc(db, 'estudiantes', ANA, c, 'x'), { ...sesion, actualizado: serverTimestamp() }));
    }
  });

  it('no lee ni escribe el progreso de otro estudiante', async () => {
    const db = como(ANA);
    await assertFails(getDoc(doc(db, 'estudiantes', LEO, 'sesiones', 's2')));
    await assertFails(setDoc(doc(db, 'estudiantes', LEO, 'sesiones', 's9'), sesion));
  });

  it('no borra su progreso', async () => {
    await assertFails(deleteDoc(doc(como(ANA), 'estudiantes', ANA, 'sesiones', 's1')));
  });

  it('no escribe fuera de sus colecciones', async () => {
    await assertFails(setDoc(doc(como(ANA), 'estudiantes', ANA, 'mensajes_tutor', 'm1'), { texto: 'hola' }));
  });

  it('lee su perfil, pero no lo cambia (ni su rol)', async () => {
    const db = como(ANA);
    await assertSucceeds(getDoc(doc(db, 'usuarios', ANA)));
    await assertFails(setDoc(doc(db, 'usuarios', ANA), { nombre: 'Ana', usuario: 'ana', rol: 'admin' }));
    await assertFails(getDoc(doc(db, 'usuarios', LEO)));
  });
});

describe('admin', () => {
  it('lee la lista de estudiantes y el progreso de todos', async () => {
    const db = como(ADMIN);
    await assertSucceeds(getDocs(query(collection(db, 'usuarios'), where('rol', '==', 'estudiante'))));
    await assertSucceeds(getDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1')));
    await assertSucceeds(getDocs(collection(db, 'estudiantes', LEO, 'sesiones')));
  });

  it('crea el perfil de un estudiante', async () => {
    await assertSucceeds(setDoc(doc(como(ADMIN), 'usuarios', 'uid-nuevo'), { nombre: 'Emi', usuario: 'emi', rol: 'estudiante' }));
  });

  it('no crea admins ni perfiles con otros campos', async () => {
    const db = como(ADMIN);
    await assertFails(setDoc(doc(db, 'usuarios', 'uid-nuevo'), { nombre: 'X', usuario: 'x', rol: 'admin' }));
    await assertFails(setDoc(doc(db, 'usuarios', 'uid-nuevo'), { nombre: 'X', usuario: 'x', rol: 'estudiante', apellido: 'Y' }));
  });

  it('no escribe ni borra progreso', async () => {
    const db = como(ADMIN);
    await assertFails(setDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1'), { ...sesion, nota: 10 }));
    await assertFails(setDoc(doc(db, 'estudiantes', ADMIN, 'sesiones', 's3'), sesion));
    await assertFails(deleteDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1')));
  });
});

describe('cuenta externa o sin sesión', () => {
  it('una cuenta sin perfil no lee ni escribe nada', async () => {
    const db = como(EXTERNO);
    await assertFails(getDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1')));
    await assertFails(setDoc(doc(db, 'estudiantes', EXTERNO, 'sesiones', 's1'), sesion));
    await assertFails(getDocs(query(collection(db, 'usuarios'), where('rol', '==', 'estudiante'))));
    await assertFails(setDoc(doc(db, 'usuarios', EXTERNO), { nombre: 'X', usuario: 'x', rol: 'estudiante' }));
  });

  it('sin iniciar sesión no hay acceso', async () => {
    const db = entorno.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'usuarios', ANA)));
    await assertFails(getDoc(doc(db, 'estudiantes', ANA, 'sesiones', 's1')));
  });
});
