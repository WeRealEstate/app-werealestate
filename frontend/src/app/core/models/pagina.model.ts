/** Página de resultados de una lista que carga por lotes ("Cargar más"). */
export interface Pagina<T> {
  contenido: T[];
  hayMas: boolean;
}
