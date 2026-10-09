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
  { version: '7.3.11', fecha: '2026-10-09', cambios: [
    "Pantalla completa totalmente disponible, bug arreglado",
  ] },
  { version: '7.3.10', fecha: '2026-10-09', cambios: [
    "Comunidades We ahora es un lienzo, zoom disponible, mover el lienzo arrastrando, mover cada tarjeta a donde quieras, reordenar arrastrando al equipo y pantalla completa ya disponible.",
    "Las lineas entre lider, linea 1 y linea 2 ya no se cortan: se dibujan de tarjeta a tarjeta y siguen a la tarjeta mientras se mueve.",
    "Soltar una tarjeta sobre un lider o sobre alguien de linea 1 la reasigna; soltarla en Independientes la saca del equipo. Cada equipo se puede ocultar o mostrar.",
  ] },
  { version: '7.3.9', fecha: '2026-10-09', cambios: [
    "Copropiedad en ventas: una venta puede tener hasta 5 clientes (el principal y 4 copropietarios), asesores internos se pueden ver en comunidades WE asi como cotizador publico queda actualizado el plano ya se puede ver desde ahí",
  ] },
  { version: '7.3.7', fecha: '2026-10-08', cambios: [
    "Plano publico: los asesores internos tambien pueden cotizar y apartar con su PIN.",
  ] },
  { version: '7.3.6', fecha: '2026-10-08', cambios: [
    "Lotes: precio por m2 propio de cada lote (hectareas y precio por manzana).",
  ] },
  { version: '7.3.5', fecha: '2026-10-08', cambios: [
    "App de escritorio: La app de escritorio fue creada con Éxito y adaptada a todos los dispositivos",
  ] },
  { version: '7.3.8', fecha: '2026-10-09', cambios: [
    "Se agrega la opcion de ver cambios en la WeApp para cada version",
  ] },
];
