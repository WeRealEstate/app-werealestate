CREATE TABLE cotizacion (
    id BIGSERIAL PRIMARY KEY,
    asesor_id BIGINT NOT NULL REFERENCES usuario(id),
    proyecto VARCHAR(50) NOT NULL,
    nombre_cliente VARCHAR(150) NOT NULL,
    manzana VARCHAR(30),
    lote VARCHAR(30),
    superficie NUMERIC(12,2) NOT NULL,
    precio_m2 NUMERIC(12,2) NOT NULL,
    precio_total NUMERIC(14,2) NOT NULL,
    forma_pago VARCHAR(100) NOT NULL,
    enganche_label VARCHAR(30) NOT NULL,
    enganche NUMERIC(14,2) NOT NULL,
    monto_financiado NUMERIC(14,2) NOT NULL,
    meses INTEGER NOT NULL,
    mensualidad NUMERIC(14,2) NOT NULL,
    interes_porcentaje NUMERIC(6,2) NOT NULL,
    interes_monto NUMERIC(14,2) NOT NULL,
    total_inversion NUMERIC(14,2) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_cotizacion_asesor ON cotizacion(asesor_id);
