-- Índices aditivos de rendimiento para consultas frecuentes en ventas, lotes, leads,
-- seguimientos, notificaciones, comisiones, gastos recurrentes y We Forms.

-- Búsqueda de ventas por lote (existsByLoteId, numeroDeVentaPorLote, findByLoteId).
-- El índice único existente (venta_id, lote_id) tiene venta_id primero y no cubre búsquedas por lote_id.
CREATE INDEX IF NOT EXISTS idx_venta_lote_lote ON venta_lote (lote_id);

-- Último seguimiento de cada lead, vencimientos de la campana y reportes por rango de fechas.
CREATE INDEX IF NOT EXISTS idx_seguimiento_lead_fecha ON seguimiento (lead_id, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_seguimiento_proximo ON seguimiento (proximo_seguimiento) WHERE proximo_seguimiento IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_seguimiento_fecha ON seguimiento (fecha);

-- Listado paginado de leads activos/archivados por último contacto y cohortes por fecha de creación.
CREATE INDEX IF NOT EXISTS idx_lead_archivado_ultimo_contacto ON lead (archivado, fecha_ultimo_contacto DESC);
CREATE INDEX IF NOT EXISTS idx_lead_fecha_creacion ON lead (fecha_creacion);

-- Filtro de leads por etiqueta (la llave primaria es (lead_id, etiqueta_id)).
CREATE INDEX IF NOT EXISTS idx_lead_etiqueta_etiqueta ON lead_etiqueta (etiqueta_id);

-- Avisos recientes de movimientos de lotes en la campana (recientesDeOtros filtra solo por fecha).
CREATE INDEX IF NOT EXISTS idx_movimiento_lote_fecha ON movimiento_lote (fecha DESC);

-- Avisos de ventas recientes en la campana (recientesDeOtros filtra por fecha_creacion).
CREATE INDEX IF NOT EXISTS idx_venta_fecha_creacion ON venta (fecha_creacion DESC);

-- Vínculo de respuestas de We Forms con su venta y avisos de registros recientes en la campana.
CREATE INDEX IF NOT EXISTS idx_we_form_respuesta_venta ON we_form_respuesta (venta_id) WHERE venta_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_we_form_respuesta_fecha_envio ON we_form_respuesta (fecha_envio DESC);

-- Búsqueda de comisiones por asesor interno o externo y vínculo con egresos.
CREATE INDEX IF NOT EXISTS idx_venta_comision_usuario_asesor ON venta_comision (usuario_asesor_id) WHERE usuario_asesor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_venta_comision_asesor_externo ON venta_comision (asesor_externo_id) WHERE asesor_externo_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_venta_comision_entrega_gasto ON venta_comision_entrega (gasto_id) WHERE gasto_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gasto_recurrente_pago_gasto ON gasto_recurrente_pago (gasto_id) WHERE gasto_id IS NOT NULL;

-- Reportes y listado de cotizaciones por fecha de creación.
CREATE INDEX IF NOT EXISTS idx_cotizacion_fecha_creacion ON cotizacion (fecha_creacion DESC);
