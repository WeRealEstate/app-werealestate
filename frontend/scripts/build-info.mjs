// Escribe src/app/build-info.ts con la fecha de compilación; se ejecuta solo antes de `npm run build`.
import { readFileSync, writeFileSync } from 'node:fs';

const hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
writeFileSync(
  new URL('../src/app/build-info.ts', import.meta.url),
  `// Generado por scripts/build-info.mjs (npm run build). La versión vive en package.json.\nexport const FECHA_COMPILACION = '${hoy}';\n`,
);

// Avisa (sin bloquear el build) si la versión actual no tiene entrada en el historial de versiones.
const { version } = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
const changelog = readFileSync(new URL('../src/app/core/changelog.ts', import.meta.url), 'utf8');
if (!changelog.includes(`version: '${version}'`)) {
  console.warn(`\n⚠ La versión ${version} (package.json) no tiene entrada en src/app/core/changelog.ts\n`);
}
