// Escribe src/app/build-info.ts con la fecha de compilación; se ejecuta solo antes de `npm run build`.
import { writeFileSync } from 'node:fs';

const hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
writeFileSync(
  new URL('../src/app/build-info.ts', import.meta.url),
  `// Generado por scripts/build-info.mjs (npm run build). La versión vive en package.json.\nexport const FECHA_COMPILACION = '${hoy}';\n`,
);
