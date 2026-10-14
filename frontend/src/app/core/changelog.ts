/**
 * Historial de versiones que se ve al hacer clic en la versión del menú.
 *
 * Se llena a mano: para publicar una versión nueva, agrega aquí una entrada con su número (formato
 * mayor.menor.parche). La versión que muestra la app (menú y página de Versiones) es la MÁS ALTA de
 * este archivo ({@link VERSION_ACTUAL}), así que no hace falta cambiar nada más para que se actualice.
 * La página de Versiones ordena y agrupa las entradas sola por versión mayor y menor.
 */
export interface EntradaVersion {
  /** Igual que en package.json, p. ej. '7.3.8'. */
  version: string;
  /** yyyy-MM-dd */
  fecha: string;
  cambios: string[];
}

export const CHANGELOG: EntradaVersion[] = [
  { version: '8.0.7', fecha: '2026-10-14', cambios: [
    "Agregado numero de cliente modificable", 
  ] },
  { version: '8.0.6', fecha: '2026-10-14', cambios: [
    "Agrego opciones de pago a Nannu y arreglo de mas bugs en precontratos", 
  ] },
  { version: '8.0.5', fecha: '2026-10-14', cambios: [
    "Arreglo BUG en los precontratos: se mostraba la pantalla de PIN mientras cargaba el precontrato", 
  ] },
  { version: '8.0.4', fecha: '2026-10-14', cambios: [
    "Ahora no siempre se pide el PIN de asesor, PIN se guarda en la memoria interna del navegador", 
  ] },
  { version: '8.0.3', fecha: '2026-10-13', cambios: [
    "Cambio de orden de numero de cliente", 
  ] },
  { version: '8.0.2', fecha: '2026-10-13', cambios: [
    "Numero de cliente agregado", 
  ] },
  { version: '8.0.1', fecha: '2026-10-13', cambios: [
    "Logos de carga por desarrollo personalizado", 
  ] },
  { version: '8.0.0', fecha: '2026-10-13', cambios: [
    "We Forms, estadisticas: nueva pestaña con total de respuestas, clientes nuevos y existentes, respuestas por dia y por asesor, y un resumen de cada pregunta (pastel o barras para opciones, lista para texto, minimo/promedio/maximo para numeros). La lista de formularios muestra el total por desarrollo.", 
  ] },
  { version: '7.3.20', fecha: '2026-10-13', cambios: [
    "We Forms, estadisticas: nueva pestaña con total de respuestas, clientes nuevos y existentes, respuestas por dia y por asesor, y un resumen de cada pregunta (pastel o barras para opciones, lista para texto, minimo/promedio/maximo para numeros). La lista de formularios muestra el total por desarrollo.",
    "We Forms, respuestas: filtros por fecha y por asesor (los usan tambien las estadisticas) y exportacion a Excel (una columna por pregunta) y a PDF (reporte con totales y resumen por pregunta).",
    "Lotes: al poner un apartado desde el panel (Lotes o Plano) administracion ahora elige el asesor al que pertenece; solo ese asesor podra usar el lote en su precontrato de We Forms.",
  ] },
  { version: '7.3.19', fecha: '2026-10-13', cambios: [
    "Nuevo modulo We Forms (solo admin y administracion con el modulo activo): formularios de precontrato con constructor de preguntas tipo Google Forms (ordenar arrastrando, ocultar, obligatorias, preguntas propias), un link publico por formulario y pestaña de respuestas.",
    "El asesor llena el precontrato con su PIN (link publico o boton Precontrato del plano, debajo de Descargar PDF). Al enviarlo se crea el cliente (se vincula por CURP, nunca por nombre, sin cambiar sus datos) y la venta automatica, con los lotes en proceso de firma; aviso a admin y administracion.",
    "Ventas de precontrato: fecha de venta provisional que administracion confirma, y aviso para aplicar el deposito de un lote apartado con dinero (baja el saldo o la mensualidad).",
  ] },
  { version: '7.3.18', fecha: '2026-10-12', cambios: [
    "Ventas: al eliminar una venta, las que tienen un numero mayor bajan 1 y no queda hueco (si eliminas la 4 de 9 ventas, quedan la 1 a la 8).",
  ] },
  { version: '7.3.17', fecha: '2026-10-12', cambios: [
    "Ventas: el numero de venta ya no se recalcula por fecha. Se asigna solo (el siguiente: mayor + 1) o se escribe a mano al registrar la venta y tambien al modificarla; si el numero ya esta ocupado, esa venta y las consecutivas que le siguen avanzan un numero.",
  ] },
  { version: '7.3.16', fecha: '2026-10-12', cambios: [
    "Cambios en la UI, tipo y expediente de contrato agregado",
  ] },
  { version: '7.3.15', fecha: '2026-10-12', cambios: [
    "Cambios en la UI, tipo y expediente de contrato agregado",
  ] },
  { version: '7.3.14', fecha: '2026-10-12', cambios: [
    "Plano publico: al entrar con el PIN de asesor aparece, debajo del boton con su nombre, el boton Descargar PDF con el plano coloreado por estado y su leyenda. Sin PIN no se muestra.",
  ] },
  { version: '7.3.13', fecha: '2026-10-12', cambios: [
    "Tipos de abonos: se agregan tipos de abonos para poder diferenciar entre abonos de enganche, abonos de mensualidad y abonos extraordinarios.",
  ] },
  { version: '7.3.13', fecha: '2026-10-12', cambios: [
    "Finanzas: se agregan campos de fecha de pago y fecha de vencimiento a los abonos, para poder llevar un control de pagos atrasados.",
    "Ventas: se agrega la opción de ver el historial de cambios de cada venta, incluyendo cambios de estatus, abonos y comentarios.",
    "Se agregan tipos de abonos"
  ] },
  { version: '7.3.12', fecha: '2026-10-09', cambios: [
    "Todos los campos numericos se escriben con comas de miles y punto decimal (dinero, superficie con decimales, porcentajes, plazos); tambien en el cotizador y promociones, que ahora admiten centavos.",
    "Clientes: Automatizados los campos de nombre, apellido y correo electronico",
    "Ventas: el enganche tambien se captura como porcentaje del precio y se ve un resumen con saldo a financiar y fecha de la ultima mensualidad (tambien en el detalle de la venta).",
    "Lotes: al capturar un lote se ve el precio total (superficie por precio por m2) en vivo.",
    "Folio requerido al registrar abonos"
  ] },
  { version: '7.3.11', fecha: '2026-10-09', cambios: [
    "Pantalla completa totalmente disponible en Comunidades We, bug arreglado",
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

const comoNumeros = (v: string): number[] => v.split('.').map((n) => Number.parseInt(n, 10) || 0);

/** La versión más alta del historial: es la que muestra la app (la única fuente de la versión). */
export const VERSION_ACTUAL: string = CHANGELOG.map((e) => e.version).reduce((mayor, v) => {
  const [a1, a2, a3] = comoNumeros(v);
  const [b1, b2, b3] = comoNumeros(mayor);
  return (a1 - b1 || a2 - b2 || a3 - b3) > 0 ? v : mayor;
});
