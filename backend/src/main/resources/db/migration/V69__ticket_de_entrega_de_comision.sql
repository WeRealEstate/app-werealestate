-- Ticket de pago de las entregas de comisión. Al entregar se elige si va con o sin IVA, con o sin factura
-- (solo se marcan, no cambian montos) y la forma de pago (EFECTIVO o TRANSFERENCIA). Para que el ticket no
-- cambie aunque después se anulen recibos o se edite la comisión, la entrega guarda cómo estaba la comisión en
-- ese momento y los abonos que cubrió (venta_comision_entrega_abono).
-- Solo agrega columnas nullable y una tabla nueva: no modifica ningún dato existente. Las entregas anteriores
-- quedan con estas columnas en null y no tienen ticket.
ALTER TABLE venta_comision_entrega
    ADD COLUMN con_iva BOOLEAN,
    ADD COLUMN con_factura BOOLEAN,
    ADD COLUMN forma_pago VARCHAR(20),
    ADD COLUMN comision_monto NUMERIC(14, 2),
    ADD COLUMN comision_porcentaje NUMERIC(9, 4),
    ADD COLUMN entregado_acumulado NUMERIC(14, 2),
    ADD COLUMN desarrollo VARCHAR(200),
    ADD COLUMN lotes VARCHAR(300);

CREATE TABLE venta_comision_entrega_abono (
    id BIGSERIAL PRIMARY KEY,
    entrega_id BIGINT NOT NULL REFERENCES venta_comision_entrega (id) ON DELETE CASCADE,
    fecha_abono DATE NOT NULL,
    concepto VARCHAR(30) NOT NULL,
    monto NUMERIC(14, 2) NOT NULL CHECK (monto > 0)
);

CREATE INDEX idx_venta_comision_entrega_abono_entrega ON venta_comision_entrega_abono (entrega_id);
