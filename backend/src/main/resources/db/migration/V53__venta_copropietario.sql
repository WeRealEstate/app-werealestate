-- Copropiedad: una venta puede tener hasta 5 clientes (el principal, que sigue en venta.cliente_id,
-- más hasta 4 copropietarios en esta tabla).
CREATE TABLE venta_copropietario (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta (id) ON DELETE CASCADE,
    cliente_id BIGINT NOT NULL REFERENCES cliente (id),
    orden INTEGER NOT NULL,
    CONSTRAINT uq_venta_copropietario UNIQUE (venta_id, cliente_id)
);
CREATE INDEX idx_venta_copropietario_cliente ON venta_copropietario (cliente_id);
