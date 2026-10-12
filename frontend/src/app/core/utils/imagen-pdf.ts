/** Solo cuando el plano NO está en JPEG (PNG o WEBP): jsPDF solo incrusta JPEG sin recomprimir, así que
 * esas imágenes se convierten a JPEG de alta calidad. Este es el lado más largo permitido en esa
 * conversión: los navegadores limitan el tamaño de un canvas (≈16,000 px por lado; menos en celulares). */
const MAX_LADO_CONVERSION_PDF_PX = 8192;

/** Descarga la imagen del plano para incrustarla en el PDF TAL CUAL (los polígonos se dibujan aparte,
 * como vectores): así el PDF conserva toda la resolución del archivo original, sin reducirla ni
 * volver a comprimirla. Las imágenes que no son JPEG (PNG/WEBP) se convierten a JPEG de calidad 0.92
 * porque jsPDF solo incrusta JPEG sin recomprimir; ver MAX_LADO_CONVERSION_PDF_PX. */
export async function leerImagenParaPdf(url: string): Promise<{ bytes: Uint8Array; ancho: number; alto: number }> {
  const respuesta = await fetch(url);
  if (!respuesta.ok) throw new Error('No se pudo descargar la imagen del plano');
  const blob = await respuesta.blob();
  const bytes = new Uint8Array(await blob.arrayBuffer());

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
