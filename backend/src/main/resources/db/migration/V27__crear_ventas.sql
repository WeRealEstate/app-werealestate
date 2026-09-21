-- Registro de ventas cerradas. Independiente de lead/usuario a propósito: no todos los clientes
-- pasan por el CRM como lead (hay asesores externos que traen compradores directo) y no todos los
-- asesores tienen cuenta en el sistema, así que "cliente" y "asesor" son texto libre, no referencias.
CREATE TABLE venta (
    id BIGSERIAL PRIMARY KEY,
    lote_id BIGINT NOT NULL REFERENCES lote(id),
    cliente VARCHAR(200) NOT NULL,
    asesor VARCHAR(200) NOT NULL,
    precio_venta NUMERIC(14, 2) NOT NULL,
    forma_pago VARCHAR(50) NOT NULL,
    fecha_venta DATE NOT NULL,
    notas VARCHAR(1000),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_venta_lote ON venta(lote_id);
CREATE INDEX idx_venta_fecha ON venta(fecha_venta);
