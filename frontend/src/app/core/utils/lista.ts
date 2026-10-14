/** Búsquedas en listas de nombres (países, estados) sin importar mayúsculas ni acentos. */

/** Minúsculas y sin acentos, para comparar lo que escribe el usuario con la lista. */
export function normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

/** El nombre de la lista que corresponde al texto (sin importar mayúsculas ni acentos), o null. */
export function canonicoEnLista(lista: readonly string[], texto: string | null | undefined): string | null {
  if (!texto) return null;
  const clave = normalizarTexto(texto);
  return lista.find((x) => normalizarTexto(x) === clave) ?? null;
}

/** Elementos que contienen el texto, en el orden de la lista; sin texto, toda la lista. */
export function buscarEnLista(lista: readonly string[], texto: string): string[] {
  const clave = normalizarTexto(texto);
  if (!clave) return [...lista];
  return lista.filter((x) => normalizarTexto(x).includes(clave));
}
