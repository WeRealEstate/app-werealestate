import { Pais } from '../models/lead.model';

const LADA_MEXICO = '52';

function soloDigitos(telefono: string): string {
  return telefono.replace(/\D/g, '');
}

/**
 * Antepone la lada de México cuando el teléfono es un número local a 10 dígitos (la forma en la
 * que casi siempre se captura un lead mexicano). Si ya trae código de país, o el lead es
 * extranjero (donde el sistema no guarda ninguna lada), se usa el número tal cual está.
 */
export function telefonoInternacional(telefono: string, pais: Pais | null): string {
  const digitos = soloDigitos(telefono);
  if (pais === 'MEXICANO' && digitos.length === 10) {
    return LADA_MEXICO + digitos;
  }
  return digitos;
}

/** Link de WhatsApp (wa.me) con el número normalizado y, opcionalmente, un mensaje prellenado. */
export function linkWhatsApp(telefono: string, pais: Pais | null, mensaje?: string): string {
  const numero = telefonoInternacional(telefono, pais);
  const query = mensaje ? `?text=${encodeURIComponent(mensaje)}` : '';
  return `https://wa.me/${numero}${query}`;
}

/** Link `tel:` para llamar directo desde el navegador/celular. */
export function linkLlamada(telefono: string, pais: Pais | null): string {
  return `tel:+${telefonoInternacional(telefono, pais)}`;
}
