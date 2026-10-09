/**
 * Historial de versiones que se ve al hacer clic en la versión del menú.
 *
 * Se llena a mano: al subir la versión en package.json, agrega aquí una entrada nueva con ese mismo
 * número (formato mayor.menor.parche). La ventana las ordena y las agrupa sola por versión mayor y menor.
 * `npm run build` avisa si la versión de package.json no tiene entrada.
 */
export interface EntradaVersion {
  /** Igual que en package.json, p. ej. '7.3.8'. */
  version: string;
  /** yyyy-MM-dd */
  fecha: string;
  cambios: string[];
}

export const CHANGELOG: EntradaVersion[] = [
  { version: '7.3.8', fecha: '2026-10-09', cambios: [
    "Se agrega la opcion de ver cambios en la WeApp para cada version.",
  ] },
];
