-- Correos de bienvenida que se le mandan al cliente titular de una venta (botón "Enviar correo de bienvenida" del
-- detalle de la venta). Solo se guardan los que salieron bien. Solo agrega una tabla: no toca datos existentes. Si se
-- elimina la venta, sus registros se borran con ella.
CREATE TABLE bienvenida_cliente (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta (id) ON DELETE CASCADE,
    enviado_en TIMESTAMP NOT NULL,
    usuario_id BIGINT REFERENCES usuario (id),
    correo_destino VARCHAR(255) NOT NULL
);
CREATE INDEX idx_bienvenida_cliente_venta ON bienvenida_cliente (venta_id, enviado_en DESC);
