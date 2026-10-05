-- Aportaciones de una venta: pagos extra programados (varios por año, cada uno en su mes y con su
-- propio monto) del esquema "Con aportaciones". Es información de la venta (qué se acordó con el
-- cliente); los pagos reales siguen registrándose como abonos (ver PagoVenta).
CREATE TABLE venta_aportacion (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    anio INTEGER NOT NULL,
    mes INTEGER NOT NULL CHECK (mes BETWEEN 1 AND 12),
    monto NUMERIC(14, 2) NOT NULL CHECK (monto > 0),
    -- Una aportación por mes de cada venta.
    CONSTRAINT uk_venta_aportacion UNIQUE (venta_id, anio, mes)
);

CREATE INDEX idx_venta_aportacion_venta ON venta_aportacion(venta_id);
