/** Lo que el plano público recuerda en el navegador: el asesor verificado (por pestaña) y los desarrollos a los
 * que el PIN guardado no tiene acceso (por dispositivo, unas horas). Todo tolera navegadores sin almacenamiento. */

const CLAVE_ASESOR = 'plano-publico-asesor-';

export function leerAsesorGuardado(proyecto: string): string | null {
  try {
    return sessionStorage.getItem(CLAVE_ASESOR + proyecto);
  } catch {
    return null;
  }
}

export function guardarAsesor(proyecto: string, nombre: string | null): void {
  try {
    if (nombre) sessionStorage.setItem(CLAVE_ASESOR + proyecto, nombre);
    else sessionStorage.removeItem(CLAVE_ASESOR + proyecto);
  } catch {
    // Sin almacenamiento: el acceso solo dura hasta recargar.
  }
}

/** Desarrollos a los que el PIN recordado NO tiene acceso (proyecto → { pin, hasta }). Evita volver a preguntarle
 * al servidor en cada visita: cada respuesta "sin acceso" cuenta como intento fallido del PIN en esa IP (ver
 * AsesorPublicoController), y con 5 se bloquea por 10 minutos a todos los que entran desde ahí. Caduca en unas
 * horas por si administración le da acceso después; escribir el PIN a mano con el botón "Asesor" también lo borra. */
const CLAVE_SIN_ACCESO = 'plano-publico-sin-acceso';
const DURACION_SIN_ACCESO_MS = 12 * 60 * 60 * 1000;

function leerSinAcceso(): Record<string, { pin: string; hasta: number }> {
  try {
    return JSON.parse(localStorage.getItem(CLAVE_SIN_ACCESO) ?? '{}');
  } catch {
    return {};
  }
}

export function sinAccesoReciente(pin: string, proyecto: string): boolean {
  const marca = leerSinAcceso()[proyecto];
  return !!marca && marca.pin === pin && marca.hasta > Date.now();
}

export function marcarSinAcceso(pin: string, proyecto: string): void {
  try {
    const marcas = leerSinAcceso();
    marcas[proyecto] = { pin, hasta: Date.now() + DURACION_SIN_ACCESO_MS };
    localStorage.setItem(CLAVE_SIN_ACCESO, JSON.stringify(marcas));
  } catch {
    // Sin almacenamiento: se vuelve a preguntar en la siguiente visita.
  }
}

export function olvidarSinAcceso(proyecto?: string): void {
  try {
    if (!proyecto) {
      localStorage.removeItem(CLAVE_SIN_ACCESO);
      return;
    }
    const marcas = leerSinAcceso();
    delete marcas[proyecto];
    localStorage.setItem(CLAVE_SIN_ACCESO, JSON.stringify(marcas));
  } catch {
    // nada que borrar
  }
}
