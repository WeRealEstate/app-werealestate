/** Solo cuando el plano NO está en JPEG (PNG o WEBP): jsPDF solo incrusta JPEG sin recomprimir, así que
 * esas imágenes se convierten a JPEG de alta calidad. Este es el lado más largo permitido en esa
 * conversión: los navegadores limitan el tamaño de un canvas (≈16,000 px por lado; menos en celulares). */
const MAX_LADO_CONVERSION_PDF_PX = 8192;

/** Mide un JPEG leyendo su marcador SOF (sin decodificar los píxeles: un plano de 150 MP no se decodifica solo para
 * saber su tamaño). null si el archivo no se deja leer así. */
export function medidasDeJpeg(bytes: Uint8Array): { ancho: number; alto: number } | null {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return null;
  let i = 2;
  while (i + 9 < bytes.length) {
    if (bytes[i] !== 0xff) return null;
    const marcador = bytes[i + 1];
    if (marcador === 0xff) {
      i++; // relleno
      continue;
    }
    if (marcador === 0xd8 || marcador === 0x01 || (marcador >= 0xd0 && marcador <= 0xd7)) {
      i += 2; // marcadores sin longitud
      continue;
    }
    const longitud = (bytes[i + 2] << 8) | bytes[i + 3];
    const esSof = marcador >= 0xc0 && marcador <= 0xcf && marcador !== 0xc4 && marcador !== 0xc8 && marcador !== 0xcc;
    if (esSof) {
      const alto = (bytes[i + 5] << 8) | bytes[i + 6];
      const ancho = (bytes[i + 7] << 8) | bytes[i + 8];
      return ancho > 0 && alto > 0 ? { ancho, alto } : null;
    }
    if (longitud < 2) return null;
    i += 2 + longitud;
  }
  return null;
}

/** Descarga la copia ligera del plano para el PDF (`urlCopia`, ver urlCopiaParaPdf) y, si no existe todavía (el
 * servidor aún no la genera) o falla, cae al plano HD. */
export async function leerImagenParaPdfConCopia(
  urlHd: string,
  urlCopia: string,
): Promise<{ bytes: Uint8Array; ancho: number; alto: number }> {
  if (urlCopia !== urlHd) {
    try {
      return await leerImagenParaPdf(urlCopia);
    } catch {
      // sin copia ligera: se usa el HD
    }
  }
  return leerImagenParaPdf(urlHd);
}

/** Descarga la imagen del plano para incrustarla en el PDF TAL CUAL (los polígonos se dibujan aparte,
 * como vectores): así el PDF conserva toda la resolución del archivo original, sin reducirla ni
 * volver a comprimirla. Las imágenes que no son JPEG (PNG/WEBP) se convierten a JPEG de calidad 0.92
 * porque jsPDF solo incrusta JPEG sin recomprimir; ver MAX_LADO_CONVERSION_PDF_PX. */
export async function leerImagenParaPdf(url: string): Promise<{ bytes: Uint8Array; ancho: number; alto: number }> {
  const respuesta = await fetch(url);
  if (!respuesta.ok) throw new Error('No se pudo descargar la imagen del plano');
  const blob = await respuesta.blob();
  const bytes = new Uint8Array(await blob.arrayBuffer());

  // JPEG: las medidas salen del encabezado, sin decodificar toda la imagen.
  const medidas = medidasDeJpeg(bytes);
  if (medidas) return { bytes, ancho: medidas.ancho, alto: medidas.alto };

  const urlTemporal = URL.createObjectURL(blob);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const imagen = new Image();
      imagen.onload = () => resolve(imagen);
      imagen.onerror = () => reject(new Error('No se pudo leer la imagen del plano'));
      imagen.src = urlTemporal;
    });

    const esJpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    if (esJpeg) {
      return { bytes, ancho: img.naturalWidth, alto: img.naturalHeight };
    }

    const escala = Math.min(1, MAX_LADO_CONVERSION_PDF_PX / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(img.naturalWidth * escala);
    canvas.height = Math.round(img.naturalHeight * escala);
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Este navegador no soporta canvas 2D');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

    const jpeg = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('No se pudo convertir el plano'))), 'image/jpeg', 0.92),
    );
    return { bytes: new Uint8Array(await jpeg.arrayBuffer()), ancho: canvas.width, alto: canvas.height };
  } finally {
    URL.revokeObjectURL(urlTemporal);
  }
}
