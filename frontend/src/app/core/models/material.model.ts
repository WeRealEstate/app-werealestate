export type CategoriaMaterial = 'COPY' | 'FLYER' | 'BROCHURE' | 'VIDEO' | 'OTRO' | 'GALERIA';
export type ProyectoMaterial = 'SAMAI' | 'NANUU' | 'AMBOS';

export const CATEGORIAS_MATERIAL: { valor: CategoriaMaterial; etiqueta: string }[] = [
  { valor: 'COPY', etiqueta: 'Copy' },
  { valor: 'FLYER', etiqueta: 'Flyer' },
  { valor: 'BROCHURE', etiqueta: 'Brochure' },
  { valor: 'VIDEO', etiqueta: 'Video' },
  { valor: 'OTRO', etiqueta: 'Otro' },
  { valor: 'GALERIA', etiqueta: 'Galería' },
];

export const PROYECTOS_MATERIAL: { valor: ProyectoMaterial; etiqueta: string }[] = [
  { valor: 'SAMAI', etiqueta: 'SAMAI Campestre' },
  { valor: 'NANUU', etiqueta: 'Aldea Nanuu' },
  { valor: 'AMBOS', etiqueta: 'Ambos desarrollos' },
];

/** Máximo por archivo; el servidor lo vuelve a validar. */
export const MAX_ARCHIVO_MATERIAL_MB = 25;

/** Galería: máximo por imagen y de imágenes (el servidor los vuelve a validar). */
export const MAX_IMAGEN_GALERIA_MB = 10;
export const MAX_IMAGENES_GALERIA = 25;

/** Una imagen de una galería (material de categoría GALERIA). */
export interface MaterialImagen {
  id: number;
  nombre: string;
  extension: string;
  tamano: number;
}

export interface MaterialItem {
  id: number;
  titulo: string;
  descripcion: string | null;
  categoria: CategoriaMaterial;
  proyecto: ProyectoMaterial;
  texto: string | null;
  archivoNombre: string | null;
  archivoExtension: string | null;
  archivoTamano: number | null;
  fechaActualizacion: string;
  /** Imágenes de la galería en orden (la primera es la portada); vacía si no es galería. */
  imagenes: MaterialImagen[];
}

export function etiquetaCategoria(c: CategoriaMaterial): string {
  return CATEGORIAS_MATERIAL.find((x) => x.valor === c)?.etiqueta ?? c;
}

export function etiquetaProyecto(p: ProyectoMaterial): string {
  return PROYECTOS_MATERIAL.find((x) => x.valor === p)?.etiqueta ?? p;
}

export function tamanoLegible(bytes: number | null): string {
  if (bytes == null) return '';
  return bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}
