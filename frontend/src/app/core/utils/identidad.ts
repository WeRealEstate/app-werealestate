/** Utilidades de identidad para el alta de clientes: edad, CURP y RFC (mismos formatos que valida el backend). */

const CURP = /^[A-Z]{4}\d{6}[HMX][A-Z]{5}[A-Z0-9]\d$/;
const RFC = /^[A-ZÑ&]{3,4}\d{6}[A-Z0-9]{3}$/;

export function curpFormatoValido(curp: string): boolean {
  return CURP.test(curp);
}

export function rfcFormatoValido(rfc: string): boolean {
  return RFC.test(rfc);
}

/** Años cumplidos a hoy de una fecha yyyy-MM-dd; null si la fecha no es válida o es futura. */
export function edadDe(fechaIso: string | null | undefined): number | null {
  if (!fechaIso || !/^\d{4}-\d{2}-\d{2}$/.test(fechaIso)) return null;
  const [anio, mes, dia] = fechaIso.split('-').map(Number);
  const hoy = new Date();
  let edad = hoy.getFullYear() - anio;
  if (hoy.getMonth() + 1 < mes || (hoy.getMonth() + 1 === mes && hoy.getDate() < dia)) edad--;
  return edad >= 0 && edad <= 120 ? edad : null;
}

/** Clave de entidad de la CURP (posiciones 12-13) → nombre. NE = nacido en el extranjero. */
const ENTIDADES: Record<string, string> = {
  AS: 'Aguascalientes', BC: 'Baja California', BS: 'Baja California Sur', CC: 'Campeche', CL: 'Coahuila',
  CM: 'Colima', CS: 'Chiapas', CH: 'Chihuahua', DF: 'Ciudad de México', DG: 'Durango', GT: 'Guanajuato',
  GR: 'Guerrero', HG: 'Hidalgo', JC: 'Jalisco', MC: 'Estado de México', MN: 'Michoacán', MS: 'Morelos',
  NT: 'Nayarit', NL: 'Nuevo León', OC: 'Oaxaca', PL: 'Puebla', QT: 'Querétaro', QR: 'Quintana Roo',
  SP: 'San Luis Potosí', SL: 'Sinaloa', SR: 'Sonora', TC: 'Tabasco', TS: 'Tamaulipas', TL: 'Tlaxcala',
  VZ: 'Veracruz', YN: 'Yucatán', ZS: 'Zacatecas',
};

export interface DatosCurp {
  /** yyyy-MM-dd */
  fechaNacimiento: string;
  /** Entidad de nacimiento; null si es nacido en el extranjero o la clave no se reconoce. */
  entidad: string | null;
}

/** Lo que dice una CURP completa: fecha de nacimiento (el dígito 17 distingue el siglo: número =
 * 1900s, letra = 2000s) y entidad. null si la CURP no está completa o su fecha no existe. */
export function datosDeCurp(curp: string): DatosCurp | null {
  if (!curpFormatoValido(curp)) return null;
  const aa = Number(curp.slice(4, 6));
  const mm = Number(curp.slice(6, 8));
  const dd = Number(curp.slice(8, 10));
  const siglo = /\d/.test(curp[16]) ? 1900 : 2000;
  const anio = siglo + aa;
  const fecha = new Date(anio, mm - 1, dd);
  if (fecha.getFullYear() !== anio || fecha.getMonth() !== mm - 1 || fecha.getDate() !== dd) return null;
  const iso = `${anio}-${String(mm).padStart(2, '0')}-${String(dd).padStart(2, '0')}`;
  return { fechaNacimiento: iso, entidad: ENTIDADES[curp.slice(11, 13)] ?? null };
}

/** "2000-05-17" → "17/05/2000" */
export function fechaCorta(iso: string): string {
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
}
