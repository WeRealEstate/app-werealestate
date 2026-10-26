// Después de `ng build` (npm run build): genera una copia de index.html por cada página pública con su
// propio título y vista previa (Open Graph). WhatsApp y las redes leen el HTML tal como lo sirve el
// servidor, sin ejecutar JavaScript, así que el título que Angular pone después no les sirve.
// Cada copia queda en <ruta>/index.html: con la configuración normal de una SPA (servir el archivo si
// existe, si no index.html) el servidor la entrega sola en esa ruta. La app arranca igual desde ella.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const SITIO = (process.env.SITE_URL ?? 'https://app.weinversiones.com').replace(/\/$/, '');
const IMAGEN = `${SITIO}/images/we-logo-color.png`;
const CARPETA = new URL('../dist/frontend/browser/', import.meta.url);

// Mismos títulos que el `title` de cada ruta en src/app/app.routes.ts. `imagen` (opcional) es un archivo de
// public/images de 1200x630 y menos de 300 KB (WhatsApp ignora las más pesadas); sin ella se usa el logo de We.
// Los planos de SAMAI y Nanuu usan una tarjeta con el logo de su desarrollo sobre el fondo oscuro de su marca.
const PAGINAS = [
  { ruta: 'cotizador-publico', titulo: 'We Real Estate | Cotizador asesores', descripcion: 'Cotiza tu terreno en SAMAI Campestre o Aldea Nanuu' },
  { ruta: 'samai', titulo: 'We Real Estate | Plano Samai', descripcion: 'Consulta la disponibilidad de lotes de SAMAI Campestre en el plano', imagen: 'samai-logo-og.png' },
  { ruta: 'aldea-nanuu', titulo: 'We Real Estate | Plano Nanuu', descripcion: 'Consulta la disponibilidad de lotes de Aldea Nanuu en el plano', imagen: 'nanuu-logo-og.png' },
  { ruta: 'we-forms', titulo: 'We Real Estate | Registrar venta', descripcion: 'Registra la venta de tu cliente' },
  { ruta: 'material', titulo: 'We Real Estate | Material', descripcion: 'Material de venta para asesores' },
];

const esc = (t) => t.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');

if (!existsSync(new URL('index.html', CARPETA))) {
  console.error('paginas-publicas: no existe dist/frontend/browser/index.html; corre primero ng build.');
  process.exit(1);
}
const base = readFileSync(new URL('index.html', CARPETA), 'utf8');

const BLOQUE = /<!-- vista-previa:inicio -->[\s\S]*?<!-- vista-previa:fin -->/;
if (!BLOQUE.test(base)) {
  console.error('paginas-publicas: index.html no tiene el bloque <!-- vista-previa:inicio --> ... <!-- vista-previa:fin -->.');
  process.exit(1);
}

for (const { ruta, titulo, descripcion, imagen } of PAGINAS) {
  // Reemplaza el título y la vista previa genéricos de index.html por los de la página.
  const bloque = [
    '<!-- vista-previa:inicio -->',
    `<title>${esc(titulo)}</title>`,
    `<meta name="description" content="${esc(descripcion)}">`,
    `<meta property="og:type" content="website">`,
    `<meta property="og:site_name" content="We Real Estate">`,
    `<meta property="og:title" content="${esc(titulo)}">`,
    `<meta property="og:description" content="${esc(descripcion)}">`,
    `<meta property="og:url" content="${SITIO}/${ruta}">`,
    `<meta property="og:image" content="${imagen ? `${SITIO}/images/${imagen}` : IMAGEN}">`,
    `<meta property="og:image:width" content="${imagen ? 1200 : 600}">`,
    `<meta property="og:image:height" content="${imagen ? 630 : 259}">`,
    `<meta name="twitter:card" content="${imagen ? 'summary_large_image' : 'summary'}">`,
    '<!-- vista-previa:fin -->',
  ].join('\n  ');
  mkdirSync(new URL(`${ruta}/`, CARPETA), { recursive: true });
  writeFileSync(new URL(`${ruta}/index.html`, CARPETA), base.replace(BLOQUE, () => bloque));
  console.log(`paginas-publicas: ${ruta}/index.html — ${titulo}`);
}
