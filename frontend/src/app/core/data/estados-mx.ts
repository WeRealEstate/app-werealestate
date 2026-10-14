import { canonicoEnLista } from '../utils/lista';

/** Los 32 estados de México en orden alfabético. Solo valen cuando el país de residencia es México; en otro
 * país el estado es texto libre. Es la misma lista que el backend (resources/estados-mx.txt, clase
 * EstadosMexico): si se edita aquí hay que editarla allá, porque el backend valida el nombre exacto. */
export const ESTADOS_MX: readonly string[] = [
  'Aguascalientes',
  'Baja California',
  'Baja California Sur',
  'Campeche',
  'Chiapas',
  'Chihuahua',
  'Ciudad de México',
  'Coahuila',
  'Colima',
  'Durango',
  'Estado de México',
  'Guanajuato',
  'Guerrero',
  'Hidalgo',
  'Jalisco',
  'Michoacán',
  'Morelos',
  'Nayarit',
  'Nuevo León',
  'Oaxaca',
  'Puebla',
  'Querétaro',
  'Quintana Roo',
  'San Luis Potosí',
  'Sinaloa',
  'Sonora',
  'Tabasco',
  'Tamaulipas',
  'Tlaxcala',
  'Veracruz',
  'Yucatán',
  'Zacatecas',
];

/** El nombre de la lista que corresponde al texto (sin importar mayúsculas ni acentos), o null. */
export function estadoCanonico(texto: string | null | undefined): string | null {
  return canonicoEnLista(ESTADOS_MX, texto);
}
