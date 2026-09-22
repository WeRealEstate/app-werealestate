-- Una venta ahora puede incluir varios lotes: un cliente que compra 2+ lotes en la misma operación
-- lleva UNA sola mensualidad/plazo/saldo combinado (igual que ya se llevaba en el Excel antes de
-- este CRM), no una cobranza separada por lote. El precio de cada lote vive aquí; el precio total
-- de la venta se calcula sumando sus líneas (ver VentaService), ya no se guarda en venta.
CREATE TABLE venta_lote (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    lote_id BIGINT NOT NULL REFERENCES lote(id),
    precio NUMERIC(14, 2) NOT NULL
);

CREATE INDEX idx_venta_lote_venta ON venta_lote(venta_id);
-- Un mismo lote no puede aparecer dos veces en la misma venta.
CREATE UNIQUE INDEX idx_venta_lote_unico ON venta_lote(venta_id, lote_id);

-- No hay ventas reales capturadas todavía (tabla vacía), así que no hace falta migrar datos.
ALTER TABLE venta DROP COLUMN lote_id;
ALTER TABLE venta DROP COLUMN precio_venta;
