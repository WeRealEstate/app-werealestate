-- Recordatorios de pago por correo que se le mandan al cliente titular de una venta (botón "Enviar recordatorio de pago"
-- del detalle de la venta). Solo se guardan los que salieron bien. Guarda lo que se recordó (el próximo pago y el atraso)
-- para saber qué se le dijo al cliente. Solo agrega una tabla: no toca datos existentes. Si se elimina la venta, sus
-- recordatorios se borran con ella.
CREATE TABLE recordatorio_pago (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta (id) ON DELETE CASCADE,
    enviado_en TIMESTAMP NOT NULL,
    usuario_id BIGINT REFERENCES usuario (id),
    correo_destino VARCHAR(255) NOT NULL,
    proximo_pago_fecha DATE,
    proximo_pago_monto NUMERIC(14, 2),
    atraso_monto NUMERIC(14, 2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_recordatorio_pago_venta ON recordatorio_pago (venta_id, enviado_en DESC);
