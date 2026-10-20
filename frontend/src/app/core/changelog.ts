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
  { version: '10.2.0', fecha: '2026-10-20', cambios: [
    "Formas de pago combinables en el Cotizador (panel y público), Registrar venta y We Forms: a Sin enganche, Con enganche o Pago inicial se les puede sumar Con anualidades o Con aportaciones. Lo que no se combina queda apagado hasta desmarcar lo elegido, y Contado va solo. Al abrir no viene nada marcado.",
    "El enganche o pago inicial se resta primero y el saldo se reparte entre las anualidades o aportaciones y las mensualidades. El resumen, la tabla de pagos y el PDF muestran el enganche y las anualidades o aportaciones por separado.",
    "Registrar venta y We Forms: las anualidades piden monto, mes y cuántas, y se guardan como aportaciones programadas de la venta (una por año), así sus abonos se registran como aportación.",
  ] },
  { version: '10.1.4', fecha: '2026-10-20', cambios: [
    "Registrar venta: si el desarrollo tiene un solo formulario activo, el botón del plano lleva directo al formulario (ya no pasa por la pantalla para elegirlo). Con más de uno se sigue mostrando la lista.",
  ] },
  { version: '10.1.3', fecha: '2026-10-20', cambios: [
    "Galerías de Material: en la página de los asesores, debajo de la imagen ya no aparece el nombre con que se subió el archivo, solo su peso.",
    "Al descargar una imagen de una galería, o todas en el ZIP, cada archivo se llama con el título de la galería y su número (por ejemplo 'Flyers SAMAI 03.jpg').",
  ] },
  { version: '10.1.2', fecha: '2026-10-20', cambios: [
    "Usuarios y Asesores externos: los PIN aparecen tapados (••••) y se ven con el botón del ojo de cada fila.",
    "Usuarios: cambiar el rol de alguien pide confirmación; si se cancela, el selector vuelve al rol que tenía.",
    "Detalle del lead: arriba queda 'Editar' y 'Archivar' y 'Borrar' pasan al menú '⋯'.",
    "Botones secundarios (Cancelar, Más, Exportar, Volver…) con relleno gris suave en lugar de contorno, como en iOS y macOS.",
    "Modo oscuro: los textos y bordes en azul We se ven en azul claro para que se lean bien sobre el fondo marino.",
  ] },
  { version: '10.1.1', fecha: '2026-10-19', cambios: [
    "Planos públicos: el asesor que ya puso su PIN en un plano (por ejemplo Samai) aparece con su sesión al abrir el otro (Aldea Nanuu) sin volver a escribirlo, si tiene acceso a ese desarrollo. La sesión se recuerda en el dispositivo hasta que toque 'salir', que cierra en ambos planos.",
    "Plano público: el botón 'Descargar PDF' ahora dice 'Descargar plano'.",
  ] },
  { version: '10.1.0', fecha: '2026-10-19', cambios: [
    "Material: nuevo tipo 'Galería' para juntar varias imágenes (renders, flyers o fotos del proyecto) en una sola tarjeta. Al crearla se eligen o arrastran hasta 25 imágenes JPG, PNG o WEBP de hasta 10 MB cada una, se ordenan arrastrando (la primera es la portada) y se suben con su avance.",
    "La tarjeta de una galería muestra un mosaico con sus primeras imágenes. En la vista previa se pasa de una imagen a otra con las flechas, el teclado o deslizando el dedo, hay una tira de miniaturas para saltar a cualquiera, y se puede descargar una imagen o todas juntas en un ZIP. También en la página de Material de los asesores.",
  ] },
  // Redacción vaga a propósito: este historial lo ve todo el equipo, y la separación de lotes en el inventario
  // es solo para admin y administración.
  { version: '10.0.1', fecha: '2026-10-19', cambios: [
    "Inicio del admin y Lotes: ajustes en cómo se cuenta y se filtra el inventario para administración.",
  ] },
  { version: '10.0.0', fecha: '2026-10-19', cambios: [
    "Nuevo estilo visual inspirado en Apple en toda la app: letra Inter, fondos gris claro con tarjetas blancas (y el azul marino de We en modo oscuro), el azul We como color de acción, y selección de texto y barras de desplazamiento con los colores de la marca.",
    "Animaciones: las pantallas del panel se funden al cambiar de una a otra, los menús nacen desde su botón y el contenido del cotizador aparece suavemente al bajar. Si el dispositivo tiene activado 'reducir movimiento', casi todo se queda quieto.",
    "Cotizador público: portada rediseñada con la foto de la costa de Oaxaca a pantalla completa, 'Cotizador We' al centro, botón 'Comenzar cotización' y enlaces a los planos de Samai y Aldea Nanuu. Al bajar, la foto se acerca y se oscurece y la barra de arriba se vuelve translúcida.",
    "Leads y Lotes: arriba queda solo el botón principal ('+ Nuevo lead' / '+ Nuevo lote') y lo demás (importar, plantilla, exportar, historial, precio por manzana) pasa a un menú 'Más'.",
    "Material: al tocar una tarjeta se abre la vista previa; copiar texto, descargar, editar y eliminar están en el menú '⋯' de cada tarjeta.",
    "Pipeline: los botones '+ Nuevo lead' y '+ Nueva tarjeta' van junto al título, y el buscador, el tablero y el contador en una fila aparte, así ya no se bajan de línea.",
    "Al hacer clic con el mouse en un botón ya no queda el contorno azul; sigue apareciendo al moverse con el teclado.",
    "Nuevo inicio del admin: el inventario de SAMAI y Aldea Nanuu con una barra por estado (cada estado lleva a Lotes ya filtrado), la cobranza del mes contra lo esperado, las comisiones por entregar el sábado y los leads fríos. Debajo, el desempeño y tus tareas lado a lado.",
    "Calendario: el mes ocupa la pantalla y lo del día elegido se ve al lado (debajo en el celular). '+ Nuevo evento', o tocar un día vacío, abre el formulario en una ventana.",
    "Detalle del lead: en pantallas grandes, registrar seguimiento a la izquierda y la bitácora a la derecha.",
    "Celular: el aviso de 'gira tu teléfono' del plano público ya no tapa el botón Asesor; en Finanzas las pestañas se deslizan de lado, las cifras no dejan huecos y cada comisión se ve como tarjeta en lugar de una tabla ancha.",
    "Inicio de sesión más sobrio: tarjeta blanca sólida sobre la foto, etiquetas visibles en los campos y botón azul sólido. El captcha sigue el modo claro u oscuro.",
    "Plano público: los botones ya no tienen destello ni rebote al aparecer; solo se aclaran al pasar el mouse.",
    "Cotizador: si 'Generar PDF' y 'Compartir' están apagados, abajo se dice qué falta (por ejemplo, el nombre del cliente o el lote). Corregidos 'Datos de la cotización', 'Precio por m²' y 'Hectárea'.",
    "El aviso 'Tienes N pendientes' sale una sola vez por inicio de sesión (no en cada recarga) y con color de aviso en lugar del verde de éxito.",
    "Las casillas del PIN ya no muestran asteriscos cuando están vacías. Pipeline dice 'tarjetas' junto al contador y los buscadores de Pipeline y Lotes ya no cortan su texto. Ajustes de redacción en varias pantallas.",
  ] },
  { version: '9.0.6', fecha: '2026-10-17', cambios: [
    "Material: en la vista previa, las flechas de anterior y siguiente ya no tapan el contenido; tienen su propio espacio a los lados del texto, la imagen, el video o el PDF.",
  ] },
  { version: '9.0.5', fecha: '2026-10-17', cambios: [
    "Bugs arregalados",
  ] },
  { version: '9.0.4', fecha: '2026-10-17', cambios: [
    "Página pública de Material rediseñada: las tarjetas ahora son como las del panel (miniatura, categoría, título, descripción, texto y botones 'Vista previa', 'Copiar texto' y 'Descargar'), sin Editar ni Eliminar. Entran en cascada y, al pasar el mouse, la tarjeta se eleva y una capa sobre la miniatura muestra el texto o la descripción completa.",
    "Material: la vista previa ahora crece desde la tarjeta que tocaste y se encoge hacia ella al cerrarla (en el panel y en la página pública). Con 'reducir movimiento' activado en el sistema no se anima.",
  ] },
  { version: '9.0.3', fecha: '2026-10-17', cambios: [
    "Plano público: el botón 'Material' ahora abre una página aparte (en otra pestaña) con el material en un grid de tarjetas: miniatura grande, título, categoría y botones 'Copiar texto' o 'Descargar'; los copys muestran el inicio del texto. Al tocar una tarjeta se abre la vista previa grande. Pide el PIN solo si este dispositivo no lo recuerda, y trae filtros por categoría y 'Cambiar de asesor'.",
  ] },
  { version: '9.0.2', fecha: '2026-10-17', cambios: [
    "Material: corregido que las imágenes aparecieran con un ícono en vez de su vista previa. Ahora, si no hay miniatura, la tarjeta muestra la imagen completa.",
  ] },
  { version: '9.0.1', fecha: '2026-10-17', cambios: [
    "Material: vista previa. Cada tarjeta muestra una miniatura de la imagen (PDF y video llevan un ícono) y, al tocarla o pulsar 'Vista previa', se abre un visor grande: la imagen ampliada, el video con reproductor o el PDF, con su texto, 'Copiar texto' y 'Descargar', y flechas para pasar al anterior o siguiente. Disponible en el panel de Material y en el botón 'Material' del plano público.",
  ] },
  { version: '9.0.0', fecha: '2026-10-17', cambios: [
    "Nuevo módulo 'Material' (debajo de We Forms): Administración sube copys (texto con botón 'Copiar'), flyers, brochures y videos (JPG, PNG, WEBP, PDF o MP4, hasta 25 MB), los asigna a SAMAI, Aldea Nanuu o ambos, y puede editarlos o eliminarlos.",
    "Plano público: nuevo botón 'Material' (entre Descargar PDF y Registrar venta) para el asesor verificado: ve el material de su desarrollo, copia los textos y ve o descarga los archivos. Usa el mismo PIN que ya recuerda We Forms; los archivos no son públicos y el servidor pide el PIN en cada consulta.",
  ] },
  { version: '8.0.24', fecha: '2026-10-17', cambios: [
    "Plano público: los botones ahora son azul sólido con borde claro, para que se noten como botones. Al pasar el mouse suben y los cruza un destello, y al aparecer entran uno tras otro.",
    "Plano público: todos los botones (asesor, Descargar PDF, Registrar venta, Seleccionar varios) quedan juntos abajo a la izquierda, apilados sobre 'Seleccionar varios'. 'Cancelar selección' sale en rojo y 'Apartar N lotes' en verde.",
  ] },
  { version: '8.0.24', fecha: '2026-10-15', cambios: [
    "Errores de seguridad en el PIN arregaldos",
  ] },
  { version: '8.0.23', fecha: '2026-10-15', cambios: [
    "Plano público: los botones de arriba a la derecha (asesor, Descargar PDF, Registrar venta) y los de selección de lotes abajo a la izquierda ahora son bastante más grandes y fáciles de tocar. En celular, 'Seleccionar varios' queda sobre los controles de zoom para no encimarse.",
  ] },
  { version: '8.0.22', fecha: '2026-10-15', cambios: [
    "Plano (interno y público): ahora se pueden apartar varios lotes a la vez (hasta 5). Botón 'Seleccionar varios' abajo a la izquierda del plano: tocas los lotes disponibles (se marcan en azul) y 'Apartar N lotes' pide una sola vez el cliente, la nota y (en el panel) el asesor. Es todo o nada: si algún lote ya no está disponible no se aparta ninguno y el aviso dice cuál.",
  ] },
  { version: '8.0.21', fecha: '2026-10-15', cambios: [
    "Íconos: Cotizador ahora lleva una calculadora y Ventas una insignia con signo de pesos; las tarjetas de Inicio, las pestañas de Finanzas y los avisos de la campana (un ícono por tipo de aviso) también tienen su ícono.",
  ] },
  { version: '8.0.20', fecha: '2026-10-15', cambios: [
    "Menú: Usuarios, Asesores externos y Comunidades We ya no comparten el mismo ícono; cada uno tiene el suyo (persona con engrane, apretón de manos y grupo de personas).",
  ] },
  { version: '8.0.19', fecha: '2026-10-15', cambios: [
    "Ventas: nueva 'Modalidad de pago' (Efectivo o Transferencia, una sola; opcional). Se elige al registrar la venta, se ve en el detalle y se cambia desde 'Modificar venta'; las ventas anteriores quedan 'Sin definir'.",
    "Ventas: el contrato firmado ahora puede ser en físico, por DocuSign o de ambas formas ('Ambos (físico y DocuSign)').",
    "Corregido: cuando el servidor fallaba o recibía una petición mal formada, la app lo confundía con una sesión vencida y te sacaba al login; ahora se ve el error real y solo se cierra sesión si de verdad venció.",
  ] },
  { version: '8.0.18', fecha: '2026-10-15', cambios: [
    "Plano público (/samai y /aldea-nanuu): modo pantalla completa, con el botón junto al zoom. Oculta la barra del navegador; la leyenda, los botones del asesor y las fichas de lote siguen igual. No aparece en navegadores que no lo permiten (iPhone).",
  ] },
  { version: '8.0.17', fecha: '2026-10-15', cambios: [
    "Plano interno: modo pantalla completa. Botón junto al zoom del plano (se ve el plano, el zoom y el botón 'Salir'; Esc también sale). Al tocar un lote se abre su ficha igual que siempre.",
  ] },
  { version: '8.0.16', fecha: '2026-10-15', cambios: [
    "Ventas (celular): en el detalle de la venta el botón 'Eliminar venta' ya no se sale de la pantalla (queda debajo de 'Modificar venta') y el nombre del cliente se ve más chico.",
  ] },
  { version: '8.0.15', fecha: '2026-10-15', cambios: [
    "Ventas: nuevo campo opcional 'Número de contrato' en el bloque Contrato del detalle de la venta (texto libre, no se puede repetir en otra venta). También aparece en las compras del detalle del cliente, donde se edita con el lápiz de cada venta.",
  ] },
  { version: '8.0.14', fecha: '2026-10-15', cambios: [
    "Corregido: al iniciar sesión como administrador o Administración el panel te regresaba al login (las notificaciones fallaban cuando un movimiento de lote no tenía nota).",
  ] },
  { version: '8.0.13', fecha: '2026-10-15', cambios: [
    "Ventas: Administración también puede eliminar una venta (con su contraseña y la misma cuenta regresiva de 10 s que el administrador).",
  ] },
  { version: '8.0.12', fecha: '2026-10-14', cambios: [
    "We Forms: donde decía 'precontrato' ahora dice 'Registrar venta' (botón del plano, pantallas del formulario, título de la pestaña y de la vista previa del link, avisos y mensajes).",
  ] },
  { version: '8.0.11', fecha: '2026-10-14', cambios: [
    "We Forms y Clientes: si el país de residencia es México, el Estado se elige de la lista de los 32 estados (con buscador); con otro país sigue siendo texto libre. Si cambian el país a México y ya habían escrito un estado que no está en la lista, se borra para que elijan uno.",
  ] },
  { version: '8.0.10', fecha: '2026-10-14', cambios: [
    "We Forms: nuevo tipo de pregunta 'País' (lista de países con buscador, México primero). La primera pregunta de ese tipo llena el país de residencia del cliente; si el cliente ya existe y ya tiene país, no se cambia.",
    "Clientes: nuevo campo 'País de residencia' (se elige de la lista) en la ficha y en el formulario del cliente.",
  ] },
  { version: '8.0.9', fecha: '2026-10-14', cambios: [
    "Vista previa al compartir links: el cotizador se ve como 'We Real Estate | Cotizador asesores', el plano de Samai como 'We Real Estate | Plano Samai', el de Nanuu como 'We Real Estate | Plano Nanuu' y el precontrato como 'We Real Estate | Precontrato'; los planos muestran la portada de su desarrollo.",
  ] },
  { version: '8.0.8', fecha: '2026-10-14', cambios: [
    "Agregado numero de cliente modificable", 
  ] },
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
