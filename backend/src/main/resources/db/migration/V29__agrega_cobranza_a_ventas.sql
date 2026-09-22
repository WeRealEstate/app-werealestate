-- Términos de financiamiento fijados al momento de la venta (opcionales: no aplican a "Contado").
ALTER TABLE venta ADD COLUMN mensualidad NUMERIC(14, 2);
ALTER TABLE venta ADD COLUMN plazo_meses INTEGER;

-- Bitácora de abonos: cada pago recibido queda como su propio registro (fecha, monto, quién lo
-- capturó), en vez de un solo campo "saldo" editable a mano que se desactualiza o se escribe mal.
-- El saldo pendiente de una venta siempre se calcula (precio_venta - suma de sus pagos), nunca se
-- guarda directo.
CREATE TABLE pago_venta (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    fecha DATE NOT NULL,
    monto NUMERIC(14, 2) NOT NULL,
    notas VARCHAR(500),
    registrado_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_pago_venta_venta ON pago_venta(venta_id);
