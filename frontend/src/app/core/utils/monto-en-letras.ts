/**
 * Monto en letras como se escribe en comprobantes en México: 1600 → "Mil seiscientos pesos 00/100 M.N.".
 * Cubre hasta 999,999,999.99 (de sobra para comisiones y ventas).
 */

const UNIDADES = [
  '', 'uno', 'dos', 'tres', 'cuatro', 'cinco', 'seis', 'siete', 'ocho', 'nueve',
  'diez', 'once', 'doce', 'trece', 'catorce', 'quince', 'dieciséis', 'diecisiete', 'dieciocho', 'diecinueve',
  'veinte', 'veintiuno', 'veintidós', 'veintitrés', 'veinticuatro', 'veinticinco', 'veintiséis', 'veintisiete',
  'veintiocho', 'veintinueve',
];
const DECENAS = ['', '', '', 'treinta', 'cuarenta', 'cincuenta', 'sesenta', 'setenta', 'ochenta', 'noventa'];
const CENTENAS = [
  '', 'ciento', 'doscientos', 'trescientos', 'cuatrocientos', 'quinientos', 'seiscientos', 'setecientos',
  'ochocientos', 'novecientos',
];

export function montoEnLetras(monto: number): string {
  const centavosTotales = Math.round(Math.abs(monto) * 100);
  const entero = Math.floor(centavosTotales / 100);
  const centavos = String(centavosTotales % 100).padStart(2, '0');
  // "un millón de pesos", pero "un millón doscientos mil pesos".
  const de = entero >= 1_000_000 && entero % 1_000_000 === 0 ? ' de' : '';
  const texto = `${apocopar(enteroEnLetras(entero))}${de} ${entero === 1 ? 'peso' : 'pesos'} ${centavos}/100 M.N.`;
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function enteroEnLetras(n: number): string {
  if (n === 0) return 'cero';
  const millones = Math.floor(n / 1_000_000);
  const miles = Math.floor((n % 1_000_000) / 1000);
  const resto = n % 1000;
  const partes: string[] = [];
  if (millones) partes.push(millones === 1 ? 'un millón' : `${apocopar(menorQueMil(millones))} millones`);
  if (miles) partes.push(miles === 1 ? 'mil' : `${apocopar(menorQueMil(miles))} mil`);
  if (resto) partes.push(menorQueMil(resto));
  return partes.join(' ');
}

function menorQueMil(n: number): string {
  if (n === 100) return 'cien';
  const centena = Math.floor(n / 100);
  const resto = n % 100;
  const partes: string[] = [];
  if (centena) partes.push(CENTENAS[centena]);
  if (resto) {
    partes.push(
      resto < 30 ? UNIDADES[resto] : DECENAS[Math.floor(resto / 10)] + (resto % 10 ? ` y ${UNIDADES[resto % 10]}` : ''),
    );
  }
  return partes.join(' ');
}

/** "uno" se acorta delante de mil, millones y pesos: "veintiún mil", "treinta y un pesos", "un peso". */
function apocopar(texto: string): string {
  return texto.replace(/veintiuno$/, 'veintiún').replace(/uno$/, 'un');
}
