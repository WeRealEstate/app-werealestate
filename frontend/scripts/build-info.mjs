// Escribe src/app/build-info.ts con la fecha de compilación; se ejecuta solo antes de `npm run build`.
import { readFileSync, writeFileSync } from 'node:fs';

const hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
writeFileSync(
  new URL('../src/app/build-info.ts', import.meta.url),
  `// Generado por scripts/build-info.mjs (npm run build). La versión vive en core/changelog.ts.\nexport const FECHA_COMPILACION = '${hoy}';\n`,
);

// La versión que muestra la app es la más alta de src/app/core/changelog.ts. Avisa (sin bloquear el
// build) si package.json quedó con otro número, para mantenerlo igual y no confundirse.
const { version } = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
const changelog = readFileSync(new URL('../src/app/core/changelog.ts', import.meta.url), 'utf8');
const versiones = [...changelog.matchAll(/version: '(\d+)\.(\d+)\.(\d+)'/g)].map((m) => m.slice(1, 4).map(Number));
const masAlta = versiones.sort((a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2]).pop();
const actual = masAlta ? masAlta.join('.') : null;
if (actual && actual !== version) {
  console.warn(`\n⚠ La app mostrará la versión ${actual} (la más alta de src/app/core/changelog.ts), pero package.json dice ${version}. Iguálalos.\n`);
}
