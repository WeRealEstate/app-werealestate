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
  { version: '11.14.10', fecha: '2026-11-10', cambios: [
    "Tabla de amortización: letra y columnas reajustadas para que nada se encime (la fecha de cada abono va sobre su monto y los renglones tienen más aire).",
  ] },
  { version: '11.14.9', fecha: '2026-11-10', cambios: [
    "Tabla de amortización: las columnas ahora tienen un ancho fijo proporcional, así que la tabla se ve completa (incluido el Estado) y alineada, sin cortes ni desplazamiento horizontal.",
  ] },
  { version: '11.14.8', fecha: '2026-11-10', cambios: [
    "Tabla de amortización: se ajustó el diseño para que quepa en el ancho disponible, sin desplazamiento horizontal (en pantallas angostas se oculta el N.º y el Saldo, y la fecha de cada abono va sobre su monto).",
  ] },
  { version: '11.14.7', fecha: '2026-11-10', cambios: [
    "Número de contrato: cambiarlo o quitarlo cuando la venta ya tiene uno ahora pide tu contraseña (con una cuenta regresiva de 10 segundos, como al eliminar una venta), tanto en el detalle de la venta como en la ficha del cliente. Capturar el primer número sigue siendo directo.",
  ] },
  { version: '11.14.6', fecha: '2026-11-10', cambios: [
    "Tabla de amortización: cuando el cliente paga más de lo que toca en un mes, el excedente solo se suma al monto pagado de ese mes; la mensualidad siguiente se queda igual (ya no se descuenta como adelanto). Si paga menos, el faltante se sigue sumando a la mensualidad siguiente.",
  ] },
  { version: '11.14.5', fecha: '2026-11-10', cambios: [
    "Tabla de amortización mes con mes: «Mensualidad» es lo que toca pagar ese mes más lo que quedó pendiente de los meses anteriores (o menos lo pagado de más), de modo que el atraso va bajando hasta el último renglón; «Monto pagado» muestra cada abono del mes con su fecha, sin sumarlos; y el estado de cada mes depende de lo que se pagó en él. También en el PDF. El saldo, los atrasos totales, Finanzas y Comisiones se calculan igual que antes.",
  ] },
  { version: '11.14.4', fecha: '2026-11-10', cambios: [
    "Abonos: ahora se puede corregir la fecha de un recibo al editarlo (Administración y Admin). Finanzas toma el abono en el mes nuevo, la tabla de amortización y las comisiones se recalculan con la fecha corregida, y el cambio queda en la bitácora del recibo. Lo que ya se pagó de comisión no se modifica.",
  ] },
  { version: '11.14.3', fecha: '2026-11-10', cambios: [
    "Tabla de amortización: nueva columna «Monto pagado» con el dinero que el cliente realmente dio cada mes, según la fecha de sus recibos (también en el PDF). Es informativa: los pagados, atrasos, estados y comisiones se calculan igual que antes.",
  ] },
  { version: '11.14.2', fecha: '2026-11-10', cambios: [
    "Cotizador: en SAMAI Campestre, los lotes mayores a 200 m² ahora se cotizan a $600 por m² cuando la forma de pago es de contado (manteniendo el precio por m² normal en cualquier otra modalidad de pago).",
  ] },
  { version: '11.14.1', fecha: '2026-11-10', cambios: [
    "Cotizador: el precio especial de contado para lotes estándar de SAMAI Campestre (200 m²) se actualizó a $120,000.",
  ] },
  { version: '11.14.0', fecha: '2026-11-10', cambios: [
    "Leads numerados: cada lead tiene un número consecutivo dentro de su asesor (1, 2, 3…) que no cambia al filtrar ni ordenar. Se ve en la lista (columna N.º), en el detalle, en las tarjetas del pipeline y en la exportación a Excel/CSV, y el buscador encuentra un lead por su número (por ejemplo «12» o «#12»). Los leads que ya existían se numeraron por su fecha de creación. Si se reasigna un lead, toma el siguiente número del asesor que lo recibe.",
  ] },
  { version: '11.13.9', fecha: '2026-11-10', cambios: [
    "Registrar abono: los meses que se pueden cobrar ahora salen de la tabla de amortización (los que aún no están pagados por completo), no de los meses que algún recibo ya marcó. Antes, si todos los meses del plazo ya estaban marcados pero faltaba dinero (por ejemplo, la última mensualidad), el formulario decía que todo estaba cubierto y no dejaba registrar el pago. Al elegir un mes, el monto sugerido es lo que le falta a ese mes.",
  ] },
  { version: '11.13.8', fecha: '2026-11-10', cambios: [
    "Lenguaje más formal: en la tabla de amortización (y su PDF) el estado «Le toca» ahora dice «Paga este mes» (o «Próximo pago» cuando ese pago cae en un mes posterior), y el resumen dice «Próximo pago el …» en Ventas, Clientes y la tabla. También se cambiaron «Gente que vende…» por «Personas que venden…», «tiene gente reportándole» por «tiene personas a su cargo» y «Suelta aquí a quien quieras sacar de un equipo» por «Arrastra aquí a quien quieras retirar de un equipo».",
  ] },
  { version: '11.13.7', fecha: '2026-11-10', cambios: [
    "Tabla de amortización: si una mensualidad ya vencida se pagó en parte, ahora sale como Pagada con lo que se abonó, y lo que faltó se suma al siguiente pago (que lo lleva como atrasado o como el que le toca). La tabla sigue sumando el precio y el atraso es el mismo dinero, pero cuenta un pago atrasado menos. Se aplica igual en Clientes, Ventas, Finanzas y Comisiones.",
  ] },
  { version: '11.13.6', fecha: '2026-11-10', cambios: [
    "Abonos: nueva casilla «Pago extemporáneo» al registrar (o corregir) una mensualidad. Sirve para clientes que ya pasaron su plazo, o ya tienen todos los meses cubiertos, y aún deben saldo: no pide meses, solo el monto, y abona al saldo y a las comisiones como una mensualidad. El recibo y su bitácora lo muestran como «Pago extemporáneo».",
  ] },
  { version: '11.13.5', fecha: '2026-11-09', cambios: [
    "Cotizador público: se agregó nuevamente el acceso directo 'Score de ventas' en la portada principal junto a los enlaces de los planos de Samai y Aldea Nanuu.",
  ] },
  { version: '11.13.4', fecha: '2026-11-09', cambios: [
    "Score de ventas: se simplificaron los periodos a 'Este mes', 'Este año' y 'Todo', eliminando las opciones 'Esta semana' y 'Rango de fechas' tanto en el panel como en la vista pública.",
  ] },
  { version: '11.13.3', fecha: '2026-11-09', cambios: [
    "Planos públicos y del panel: carga progresiva en dos pasos. Al abrir el plano se muestra primero una vista rápida (~450 KB en vez de ~26.5 MB) para que el mapa y los lotes estén listos al instante, mientras en segundo plano se descarga y decodifica la imagen en alta definición completa (15,000 × 10,000 px) y se reemplaza sola sin mover el zoom ni la posición. El servidor genera esta vista rápida y optimiza la compresión de los planos ya subidos de forma automática al iniciar.",
  ] },
  { version: '11.13.2', fecha: '2026-11-09', cambios: [
    "Planos públicos (/samai y /aldea-nanuu): se corrigió un fallo por el que al abrir el enlace directo la pantalla podía quedarse en negro hasta recargar la página (cuando la imagen precargada terminaba antes de montarse el visor o tras actualizar la app). Además, si la red falla al pedir los lotes o la imagen, se reintenta automáticamente y se muestra un botón 'Reintentar'.",
  ] },
  { version: '11.13.1', fecha: '2026-11-09', cambios: [
    "Cotizador público: el resumen del Score de ventas ya no aparece arriba del formulario ni como enlace en el centro de la portada; ahora va al final de la página como una cinta discreta de una línea con el podio (1.º a 3.º, solo posición y nombre) que al tocarla lleva al score completo. El acceso en la barra superior sigue igual.",
  ] },
  { version: '11.13.0', fecha: '2026-11-09', cambios: [
    "Respuestas más rápidas en todo el panel: el servidor ya no busca al usuario tres veces por petición (lo hace una sola vez al validar su sesión) y se agregaron índices en la base de datos para acelerar búsquedas por desarrollo, cliente, asesor, venta y fechas.",
    "Clientes, Comisiones, Finanzas y Gastos recurrentes: abrir la ficha de un cliente, buscar en el selector de clientes o registrar, editar y devolver pagos de comisiones ahora consulta solo los registros implicados en vez de leer todas las ventas, pagos y comisiones.",
    "Lotes, Leads, Campana, We Forms y Comercializadoras: las listas y selectores de lotes ya no descargan los trazos del plano cuando no se dibuja el mapa, el resumen de leads se calcula directo en la base de datos y la campana, los conteos de formularios y de asesores externos ya no hacen una consulta por fila.",
    "Material y Planos: los archivos y miniaturas de Material se transmiten directo desde el disco sin cargarlos completos en la memoria del servidor y el navegador los reutiliza si no han cambiado (sigue validando la sesión o el PIN en cada acceso). Subir un plano en PDF ocupa la mitad de memoria al convertirlo a imagen.",
  ] },
  { version: '11.12.2', fecha: '2026-11-09', cambios: [
    "Planos públicos: el zoom y el arrastre se sienten más fluidos (menos trabajo del navegador en cada paso). El comportamiento del zoom es el mismo.",
  ] },
  { version: '11.12.1', fecha: '2026-11-09', cambios: [
    "Seguridad: si se desactiva a un usuario, pierde el acceso en su siguiente petición (antes su sesión seguía viva hasta 8 horas) y los cambios de rol se aplican de inmediato. Las respuestas JSON del servidor viajan comprimidas.",
  ] },
  { version: '11.12.0', fecha: '2026-11-09', cambios: [
    "Registrar venta (We Forms): cada registro que llega manda un correo a finanzas.weinmobiliaria@gmail.com con el asunto '¡Es hora de realizar un contrato!' y los datos del cliente, el asesor, el desarrollo, los lotes, el número de venta y el formulario. Sale de la cuenta de Gmail configurada en el servidor y solo cuando el registro ya quedó guardado; el asesor no espera al correo para terminar. Mientras no se configure esa cuenta, no se manda nada.",
  ] },
  { version: '11.11.0', fecha: '2026-11-07', cambios: [
    "La app hace menos llamadas al servidor. La campana ya no recalcula las notificaciones en cada cambio de pantalla: se actualiza cada minuto, al abrirla y al navegar si pasaron más de 30 segundos. El inicio del asesor ya no las pide dos veces y muestra sus conteos de leads sin descargarlos todos. Finanzas carga Comisiones solo al abrir esa pestaña. Los desarrollos, usuarios y asesores se reutilizan durante 5 minutos y se vuelven a pedir al editarlos o al cambiar de sesión.",
    "Pantallas más ligeras: las herramientas de Excel y PDF se descargan solo al exportar, importar o generar un PDF. Leads baja de ~1 MB a ~110 KB, el Cotizador de ~650 KB a ~220 KB y el Plano de ~600 KB a ~170 KB. El primer Excel o PDF de cada sesión tarda un momento más.",
    "Cambiar de pantalla es más rápido: después de abrir la app se descargan en segundo plano las pantallas que ese usuario puede usar, y no las de otros roles. No se hace si el celular tiene ahorro de datos o una conexión 2G/3G.",
    "El servidor responde más rápido en listas grandes. Ventas, Clientes, Valor vendido y el historial de lotes ya no hacen una consulta por cada registro; por ejemplo, la lista de Ventas pasa de 60 a 12 consultas por página. La campana calcula los seguimientos pendientes con una sola consulta en vez de leer todos los leads y todos sus seguimientos.",
    "Los planos públicos y el cotizador público ya no reciben quién hizo el último cambio de cada lote ni con cuánto dinero se apartó: eran datos internos que no se mostraban. El Calendario ya no pide seguimientos de leads a quien no tiene ese módulo; esa petición fallaba siempre.",
  ] },
  { version: '11.10.1', fecha: '2026-11-05', cambios: [
    "Lotes y Plano: un lote que está en una venta pero aparece en otro estado (por ejemplo, Apartado con dinero) ahora dice 'No coincide con su venta' y trae el botón 'Alinear con la venta #N'. Con un clic, sin llenar ninguna ventana, el lote queda como lo indica su venta: Vendido si el contrato ya está firmado, o En proceso de firma si todavía no. El cliente se toma de la venta y en el historial queda la nota 'Alineado con la venta #N'. Igual que al cambiarlo a mano, el lote deja de mostrar el dinero con el que estaba apartado. El aviso de la campana de ese lote desaparece solo. Solo Admin y Administración.",
  ] },
  { version: '11.10.0', fecha: '2026-11-05', cambios: [
    "Se quitó la página pública de lotes (/cotizador-publico/lotes); quien entre a esa dirección llega al cotizador. Los planos públicos de SAMAI y Aldea Nanuu siguen igual, incluido apartar con el PIN del asesor, y en el panel no cambia nada.",
    "Avisos de lotes que no cuadran con Ventas, en la campana (y como notificación push) para Admin y Administración, uno por lote: un lote marcado como Vendido sin una venta registrada, y un lote que está en una venta pero no aparece como Vendido ni En proceso de firma. Al tocarlo se abre Lotes con ese lote ya filtrado para corregirlo. No se pueden descartar: desaparecen solos cuando el lote se corrige (registrando su venta, cambiándolo a Reservado por We o poniéndolo como Vendido o En proceso de firma, según el caso).",
    "Los lotes Vendidos sin venta registrada se siguen viendo como 'Reservado por We' para Admin y Administración, y como Vendidos en los planos públicos y el cotizador.",
  ] },
  { version: '11.9.1', fecha: '2026-11-04', cambios: [
    "Finanzas → Comisiones: textos más claros y formales en toda la pestaña. Los conceptos ahora se llaman Comisión generada, Pagada, Por pagar (Vencida, Programada para el sábado y Fechas posteriores), Retenida (ventas sin contrato firmado) y Por generar. El botón 'Entregar' ahora dice 'Registrar pago', las comisiones retrasadas se llaman Vencidas y el ticket se llama comprobante.",
    "Desglose de comisiones: la vista 'Cómo se reparte' ahora se llama 'Composición' y cada parte trae una explicación breve.",
    "Pagos del sábado: '¿De qué abonos sale?' ahora es 'Ver cálculo', una tabla con la fecha, el concepto, el abono del cliente, el porcentaje que se aplica y la comisión que genera. Abajo vienen la comisión generada al corte, lo pagado anteriormente, el total a pagar y la regla con la que se calcula. En celular se muestra un renglón por abono.",
    "Lista de comisiones: los estados ahora son Por generar, En generación, Pago parcial, Pagada y Cancelada; el detalle muestra 'Comisión generada' y 'Pagos realizados'. Los avisos y ventanas usan los mismos términos.",
  ] },
  { version: '11.9.0', fecha: '2026-11-04', cambios: [
    "Lotes, Plano y Ventas alineados: el estado de un lote que está en una venta lo manda la venta. Sin el contrato firmado queda En proceso de firma y, firmado, Vendido. En Lotes y en el Plano ese lote ya no tiene selector de estado: aparece 'Venta #N' para abrir su venta. Para liberarlo hay que eliminar la venta.",
    "Un lote sin venta ya no se marca a mano como Vendido ni En proceso de firma: al elegir uno de esos estados en Lotes o en el Plano, la app ofrece abrir 'Nueva venta' con el desarrollo y el lote ya elegidos, y el lote cambia al guardar la venta.",
    "Nueva venta: se quitó la casilla 'Marcar como Vendido'. Los lotes quedan En proceso de firma o, si el contrato ya está firmado, Vendido. Ya no se ofrecen lotes que estén en otra venta, y el servidor tampoco deja poner un lote en dos ventas.",
    "Contrato de la venta: al marcarlo como firmado, sus lotes pasan a Vendido; si se le quita la firma, regresan a En proceso de firma. Corregir solo la fecha, el medio o el enlace de Drive no los mueve. Cada cambio queda en el historial del lote.",
    "Eliminar una venta: sus lotes siempre vuelven a Disponible, también los que estaban En proceso de firma (antes se quedaban así, sin venta). Se quitó la casilla 'Dejar los lotes como Disponible'. Un lote con venta que haya quedado en otro estado (de antes de este cambio) solo puede volver a Vendido o En proceso de firma, no se puede apartar desde los planos públicos y no se libera solo por vencimiento.",
  ] },
  { version: '11.8.0', fecha: '2026-11-03', cambios: [
    "Clientes: ahora se ve si un cliente tiene pagos atrasados. En la lista aparece una etiqueta roja 'Atrasado · 2 mensualidades ($X)' debajo del saldo, y el filtro nuevo 'Con pagos atrasados' muestra solo a esos clientes. Un pago cuenta como atrasado desde el día siguiente a su fecha; las ventas de contado con saldo también cuentan desde el día siguiente a la venta. Cuando lo atrasado no son solo mensualidades (enganche, aportación o contado) dice 'pagos'.",
    "Ficha del cliente: nueva tarjeta 'Pagos atrasados' (monto, cuántos y desde cuándo) y una columna 'Estado' en Compras (Atrasado, Al corriente o Liquidada, y cuándo le toca su siguiente pago). Si es copropietario de una venta atrasada, también le aparece.",
    "Tabla de amortización de cada compra, en la ficha del cliente (debajo de Compras, una por compra) y en el detalle de la venta (junto a la cobranza, con el aviso de atraso): número de pago, fecha, concepto, monto, cuánto lleva pagado, saldo y estado (Pagado, Atrasado, Le toca o Pendiente). Lo abonado cubre los pagos del más antiguo al más nuevo. El botón 'Descargar PDF' la genera con el diseño de la cotización para dársela al cliente, con sus datos, los de la venta y el resumen a la fecha.",
    "Si la mensualidad redondeada deja unos centavos sin cubrir el precio, la última mensualidad los absorbe. En ventas anteriores con anualidades sin mes, esa parte queda en un renglón 'Sin fecha en el plan', que no cuenta como atraso.",
    "Finanzas → Comisiones: el atraso del cliente usa el mismo cálculo: ahora también marca las ventas de contado con saldo y cuenta los pagos atrasados de la tabla. Por ejemplo, una aportación atrasada más una mensualidad dice '2 pagos atrasados'; antes podía decir '3 mensualidades atrasadas'.",
  ] },
  { version: '11.7.2', fecha: '2026-11-03', cambios: [
    "Cotizador (panel y público): se quitó la flecha doble del menú 'Precio por m²'. El campo 'Superficie personalizada' sigue a la vista, pero se ve desactivado y no se puede editar hasta que eliges 'Personalizada' (o la tarifa por hectárea).",
    "Cotizador: el resumen ya no dice 'Mensualidad estimada $0.00 por 0 meses'. Sin forma de pago elegida muestra 'Elige una forma de pago para ver la mensualidad', y con Contado muestra 'Pago de contado' con el total en un solo pago.",
    "Cotizador: los avisos como 'Este dispositivo no permite compartir el PDF…' salen con el aviso amarillo de la app en lugar de la ventana del navegador.",
    "Cotizador público: la barra de arriba, el resumen del Score y el pie quedan alineados con el formulario.",
    "Fechas en español en toda la app: los meses ya no salen en inglés ('28 oct 2026' en lugar de '28 Oct 2026'). El detalle de la venta y la ficha del lote muestran la fecha como 12/09/2026 en lugar de 2026-09-12.",
    "Montos: en Finanzas → Ingresos las diferencias negativas salen '-$27,750.00' (antes '$-27,750.00'). El precio de los lotes (Lotes, ficha del lote en el Plano y en el plano público) y el presupuesto del lead llevan signo de pesos: '$160,000' en lugar de '160,000 MXN'.",
    "Leads (admin): el título dice de quién son los leads que estás viendo ('Mis leads', 'Leads de Mariana López' o 'Leads de todos los asesores'). Si la lista sale vacía por el filtro de asesor, aparece el botón 'Ver los de todos los asesores'.",
    "Historial de movimientos de lotes: un apartado hecho en el panel muestra quién lo hizo y, abajo, para qué asesor (por ejemplo 'Administrador We' y 'para Sofía Ramírez'). Antes aparecía como si el asesor lo hubiera hecho desde la página pública. Lo hecho desde las páginas públicas sigue diciendo '(vía disponibilidad pública)'.",
    "Registrar abono: '+ Agregar otro concepto al mismo folio' ya no queda pegado al botón 'Registrar abono'.",
  ] },
  { version: '11.7.1', fecha: '2026-11-02', cambios: [
    "Finanzas → Comisiones → Desglose: 'Cómo se reparte' ahora es una barra de colores que muestra de un vistazo qué parte del total es lo entregado, lo por entregar, lo detenido por contrato sin firmar y lo que falta por generar, con lo ganado marcado debajo. Abajo, cada parte con su color, monto, % del total y una línea de explicación; en 'Por entregar' se ve cuánto está retrasado, cuánto toca el sábado y cuánto más adelante.",
  ] },
  { version: '11.7.0', fecha: '2026-11-02', cambios: [
    "Finanzas → Comisiones: nueva sección 'Desglose' debajo de las tarjetas, para que las cifras cuadren. 'Cómo se reparte' muestra de dónde sale cada número: las comisiones totales son lo que falta por generar más lo ganado por abonos, y lo ganado es lo entregado, lo por entregar (retrasado, lo del próximo sábado y lo de más adelante) y lo detenido porque el contrato de la venta no está firmado, que antes no aparecía en ninguna tarjeta.",
    "'Por asesor' y 'Por desarrollo' muestran esas mismas cifras por cada asesor y por desarrollo; las ventas con lotes de SAMAI y de Nanuu van en 'Varios desarrollos' y las comisiones de ventas eliminadas en 'Venta eliminada'.",
    "Por entregar el sábado: ahora va agrupado por asesor, con el total a pagarle a cada uno. En cada venta, '¿De qué abonos sale?' muestra los abonos del cliente que forman el monto (fecha, tipo, monto y si cuenta completo o la mitad) menos lo ya entregado.",
    "Comisiones canceladas: ya no cuentan en 'Retrasadas', no salen en la lista del sábado y no se pueden entregar; si se reactivan, vuelven a contar.",
  ] },
  { version: '11.6.1', fecha: '2026-10-28', cambios: [
    "Score de ventas (panel, página pública y resumen del cotizador): ahora solo compiten los asesores, es decir los usuarios con rol Asesor y los asesores externos. Las ventas de Administrador, Administración y Equipo interno ya no aparecen ni suman al total del periodo. Si uno de ellos compartió una venta con un asesor, al asesor le sigue contando. Las ventas de asesores inactivos siguen sumando al total, aunque ellos no aparezcan en el ranking.",
  ] },
  { version: '11.6.0', fecha: '2026-10-27', cambios: [
    "Score de ventas: se quitó el filtro Todos / Internos / Externos y las etiquetas Interno / Externo (también la columna Tipo), en el panel y en el cotizador público. El ranking siempre muestra a todos juntos.",
    "Cotizador público: debajo de la portada aparece un resumen corto del score, con el top 5 general (de todo el historial) y el enlace 'Ver score completo'. Si el dispositivo tiene guardado el PIN del asesor, marca 'Tú' o, si no está entre los cinco, dice en qué lugar va. Si todavía no hay ventas, no se muestra.",
  ] },
  { version: '11.5.0', fecha: '2026-10-27', cambios: [
    "Score de ventas en el cotizador público (/cotizador-publico/score), para una competencia sana entre asesores internos y externos: el mismo ranking del panel (podio, tabla, periodos y filtro), sin sesión ni PIN. Se entra desde 'Score de ventas' en la barra de arriba del cotizador o desde la portada; la página entra directo al ranking, sin la foto de portada.",
    "Si el dispositivo ya tiene guardado el PIN de un asesor (el que usa en los planos, Material o Registrar venta), su fila se marca con 'Tú' y arriba ve en qué lugar va; esta página nunca pide el PIN. Si ese PIN ya no sirve, se olvida. Un PIN que no sirve cuenta en el mismo tope de intentos que las demás páginas públicas.",
  ] },
  { version: '11.4.0', fecha: '2026-10-27', cambios: [
    "Nuevo módulo 'Score de ventas' (menú, debajo de Ventas): un ranking de quién lleva más ventas. Cuentan todas las ventas del módulo Ventas por su fecha de venta, también las que entran por We Forms. Arranca en el mes en curso y se puede cambiar a esta semana, este año, todo el historial o un rango de fechas.",
    "Arriba se ve el podio con los tres primeros lugares y abajo la tabla completa con el lugar, el asesor, si es interno o externo, sus ventas y sus lotes. Internos y externos compiten juntos, con un filtro para ver solo a unos u otros. Con las mismas ventas comparten lugar (1, 2, 2, 3).",
    "Una venta compartida cuenta completa para sus dos asesores (el copropietario, mientras su comisión no esté cancelada). Los asesores inactivos no aparecen. Lo ven el admin y Administración; el admin se lo puede quitar a quien quiera desde Usuarios.",
  ] },
  { version: '11.3.0', fecha: '2026-10-27', cambios: [
    "We Forms: cada formulario tiene ahora un botón 'Duplicar'. Abre un diálogo donde eliges el desarrollo de la copia (viene elegido el otro: de SAMAI a Aldea Nanuu y al revés), el nombre y la descripción. La copia lleva todas las preguntas del original tal como están (orden, obligatorias, ocultas, opciones y su vínculo con el cliente o la venta), nace activa con su propio link y sin respuestas, y los lotes que ofrece son los del desarrollo elegido.",
  ] },
  { version: '11.2.9', fecha: '2026-10-26', cambios: [
    "Comercializadoras: la líder ahora también puede ser un asesor interno (usuario con rol Asesor), no solo un asesor externo. En el selector aparece con la etiqueta '(interno)'. Al elegirlo pasa a ser Líder en Comunidades We y, como con una líder externa, quienes se unan por el enlace de la comercializadora quedan debajo de él. Un asesor interno que ya reporta a alguien en Comunidades We no se puede elegir, y no se puede quitar de Comunidades We (ni borrar su usuario) mientras sea líder de una comercializadora.",
  ] },
  { version: '11.2.8', fecha: '2026-10-26', cambios: [
    "Planos públicos y cotizador público: los lotes 'Reservado por We' ahora salen siempre como 'Vendido' en esos links, sin importar la sesión que haya en el navegador. Antes, quien tenía abierta una sesión de Administración o Admin en el mismo navegador veía 'Reservado por We' al abrir /samai o /aldea-nanuu. Ese estado solo se ve dentro del panel (Lotes y Plano).",
  ] },
  { version: '11.2.7', fecha: '2026-10-26', cambios: [
    "Planos públicos (/samai y /aldea-nanuu) más rápidos: los lotes y la imagen del plano ahora se piden desde que se abre la página, en paralelo con la descarga de la app, en lugar de esperar a que esta arranque. El plano aparece con un fundido corto (antes se revelaba con una máscara y un filtro animados durante 2.4 s que se sentían pesados en equipos modestos) y los botones entran más pronto.",
  ] },
  { version: '11.2.6', fecha: '2026-10-26', cambios: [
    "Vista previa al compartir por WhatsApp: el link del plano de SAMAI (/samai) ahora muestra el logo de SAMAI Campestre y el de Aldea Nanuu (/aldea-nanuu) el logo de Nanuu, sobre fondo oscuro, en lugar de la foto del desarrollo.",
  ] },
  { version: '11.2.5', fecha: '2026-10-26', cambios: [
    "Páginas públicas más rápidas: la foto de portada del cotizador público pasó de 2.2 MB a unos 160 KB (WebP, con JPG de respaldo) sin pérdida visible, el script del captcha ya solo se descarga en la pantalla de inicio de sesión (antes lo bajaban todas las páginas públicas) y las fuentes de Google ya no retrasan el primer pintado.",
  ] },
  { version: '11.2.4', fecha: '2026-10-26', cambios: [
    "Plano público: los botones del asesor (Descargar plano, Material, Registrar venta, Seleccionar varios lotes y Salir) ahora están en un menú desplegable con su nombre, como la lista de 'Estados'. Arranca plegado, se abre hacia arriba y se cierra solo al elegir una opción. Con la selección de varios lotes activa, el nombre muestra cuántos llevas (por ejemplo 2/5) y 'Apartar' aparece dentro del menú.",
  ] },
  { version: '11.2.3', fecha: '2026-10-26', cambios: [
    "Comisiones: el comprobante en PDF de cada entrega ahora trae, dentro del talón del asesor, un espacio para que firme de recibido: leyenda de conformidad con el monto en número y letra, línea de firma y su nombre debajo. Para que todo siga cabiendo en una hoja, el bloque de abajo se subió un poco; si la entrega cubre más de 4 abonos, ese bloque pasa a una segunda hoja.",
  ] },
  { version: '11.2.2', fecha: '2026-10-23', cambios: [
    "Comercializadoras: en el formulario del enlace, cada integrante ahora puede elegir su propio PIN de 4 letras o números (lo escribe dos veces para no equivocarse). Si lo deja vacío, el sistema le asigna uno como antes. Si el PIN ya lo tiene otra persona, se le pide elegir otro sin decir de quién es, y hay un tope de intentos para que nadie use el formulario para averiguar qué PINs existen.",
  ] },
  { version: '11.2.1', fecha: '2026-10-23', cambios: [
    "Comercializadoras: ahora se pueden modificar y quitar integrantes desde su ventana de 'Integrantes'. Modificar cambia nombre, celular, correo y PIN (se puede escribir uno o pedir uno nuevo al azar; el anterior deja de servir). Quitar elimina a la persona del sistema y de Comunidades We; si ya tiene ventas, comisiones, clientes o registros de venta, solo se desactiva (su PIN deja de servir) y aparece como Inactivo. El contador cuenta solo a los activos.",
  ] },
  { version: '11.2.0', fecha: '2026-10-23', cambios: [
    "Clientes: el número de cliente ahora es por desarrollo, con su propia numeración en SAMAI (S-1, S-2…) y en Aldea Nanuu (N-1, N-2…). Un cliente que compró en los dos tiene uno en cada uno (por ejemplo 'S-40 · N-12').",
    "El número se recibe con la primera venta en ese desarrollo; registrar un cliente sin venta ya no le da número. Los copropietarios toman el mismo número del titular, y quien ya tiene número en un desarrollo lo conserva aunque vuelva a comprar.",
    "Los clientes que ya existían se numeraron por desarrollo en el orden de su número anterior. La lista ordena SAMAI primero y luego Nanuu, se puede buscar por 'S-40' o 'N-12', y en la ficha se edita cada número por separado.",
  ] },
  { version: '11.1.0', fecha: '2026-10-23', cambios: [
    "Nuevo apartado 'Comercializadoras' (menú, debajo de Asesores externos): se crea una comercializadora con su líder (un asesor externo) y se copia su enlace. La líder lo comparte con su equipo de ventas: cada integrante escribe su nombre, celular y correo, entra directo al equipo de la líder en Comunidades We y ve su PIN de asesor (el mismo con el que entra a los planos y a Registrar venta). Se puede ver quién se unió, generar un enlace nuevo si se filtra o desactivar la comercializadora.",
    "We Forms: nueva sección 'Altas de comercializadoras' con el enlace de cada una y cuántos integrantes se han unido.",
  ] },
  { version: '11.0.0', fecha: '2026-10-23', cambios: [
    "We Forms: el administrador puede conectar una cuenta de Google Drive y elegir, con el selector de Google, la carpeta donde van los expedientes. Desde entonces, los archivos que se suben al registrar una venta (INE, comprobantes) se guardan ahí, con una carpeta por cliente que lleva su nombre ('Laura Méndez Ruiz') y cada archivo con el nombre de su pregunta ('INE frente.jpg'). Los de registros anteriores también se suben solos.",
    "Ya subido a Drive, el archivo deja de guardarse en el servidor; desde el registro de venta se sigue abriendo igual. Si Drive no responde o se desconecta la cuenta, los archivos esperan en el servidor y no se pierden.",
    "Clientes: si el expediente no tiene enlace, la 'Carpeta de Drive' se llena sola con la carpeta del cliente y la ubicación queda en Drive. Un enlace puesto a mano no se cambia.",
  ] },
  { version: '10.4.1', fecha: '2026-10-23', cambios: [
    "Lotes: nuevo estado 'Reservado por We', para los lotes que la empresa se guarda. Solo Admin y Administración lo ven y lo pueden poner o quitar (con nota), en el plano, en Lotes y en las cifras del inicio. Para asesores, planos públicos y cotizador público el lote aparece como Vendido: lo oculta el servidor, y en el historial el movimiento sale sin nota, cliente ni quién lo hizo. Un lote reservado no se puede apartar ni cotizar.",
  ] },
  { version: '10.3.4', fecha: '2026-10-22', cambios: [
    "Gastos: ahora se pueden modificar y eliminar los egresos, también los de pago de comisiones (Admin y Administración). 'Modificar' cambia concepto, fecha, monto y comprobante; si es el egreso de una entrega de comisión, la entrega y su ticket se actualizan igual (el monto nuevo no puede pasar de lo ganado). 'Eliminar' pide tu contraseña y, en una comisión, anula la entrega: vuelve a quedar pendiente por pagar. Los pagos de gastos recurrentes siguen deshaciéndose con 'Deshacer pago'.",
  ] },
  { version: '10.3.3', fecha: '2026-10-22', cambios: [
    "Ticket de entrega de comisión: 'Falta por pagar' ahora dice 'Pendiente por pagar'. En 'Método de entrega' solo aparece cómo se entregó (efectivo o transferencia); ya no se muestran IVA, factura ni gasto (siguen guardados y visibles dentro del sistema).",
    "Ventas: la modalidad de pago del cliente ahora también puede ser 'Depósito' (además de efectivo y transferencia), al registrar la venta y en 'Modificar venta'.",
  ] },
  { version: '10.3.2', fecha: '2026-10-22', cambios: [
    "Planos públicos (/samai y /aldea-nanuu): se quitó la animación de carga y la espera mínima de 3.5 s. Ahora se ve negro mientras llega el plano y, en cuanto está, empieza la animación de revelado. Los planos que ya están en el caché del navegador se muestran casi al instante.",
  ] },
  { version: '10.3.1', fecha: '2026-10-22', cambios: [
    "Material (panel): ahora está separado por pestañas — SAMAI Campestre, Aldea Nanuu y Compartido (lo marcado 'Ambos desarrollos'), cada una con su contador. 'Nuevo material' nace en el proyecto de la pestaña abierta y, al guardar en otro proyecto, te lleva a su pestaña. Se quitó el filtro por desarrollo.",
  ] },
  { version: '10.3.0', fecha: '2026-10-21', cambios: [
    "Finanzas → Comisiones: al entregar una comisión se elige si va con o sin IVA, con o sin factura y la forma de pago (efectivo o transferencia). Las tres son obligatorias; IVA y factura solo se marcan, no cambian el monto.",
    "Cada entrega tiene su ticket de pago en PDF (folio COM-…): total entregado con monto en letras, desglose por abono, cuánto falta por pagar de la comisión, método de entrega, totales y talón para el asesor. Se descarga solo al entregar y se puede volver a bajar con el botón 'Ticket' en el detalle de la comisión. Las entregas hechas antes de este cambio no tienen ticket.",
  ] },
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
