-- Correos de liquidación (venta pagada por completo): uno al cliente titular y otro a finanzas ("¡Es hora de realizar
-- una carta finiquito!"), disparados por el botón "Enviar correos de liquidación" del detalle de la venta. Cada fila es
-- un envío en el que salió bien al menos el de finanzas. correo_cliente es null si el titular no tenía correo (solo se
-- avisó a finanzas); si lo tenía pero el envío al cliente falló, queda el correo y cliente_enviado = false. Solo agrega
-- una tabla: no toca datos existentes. Si se elimina la venta, sus registros se borran con ella.
CREATE TABLE liquidacion_aviso (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta (id) ON DELETE CASCADE,
    enviado_en TIMESTAMP NOT NULL,
    usuario_id BIGINT REFERENCES usuario (id),
    correo_cliente VARCHAR(255),
    cliente_enviado BOOLEAN NOT NULL,
    finanzas_enviado BOOLEAN NOT NULL
);
CREATE INDEX idx_liquidacion_aviso_venta ON liquidacion_aviso (venta_id, enviado_en DESC);
