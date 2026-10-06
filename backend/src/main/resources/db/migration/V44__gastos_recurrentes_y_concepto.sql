-- Los gastos dejan de ir por "tipo": cada uno lleva su concepto (texto libre) y su origen
-- (UNICO: compra de una sola vez; RECURRENTE: pago de un gasto recurrente como renta o luz;
-- COMISION: entrega de una comisión de venta). Los existentes conservan el nombre de su tipo como
-- concepto. tipo_gasto_id queda opcional (solo lo usa el gasto automático de las comisiones).
ALTER TABLE gasto ALTER COLUMN tipo_gasto_id DROP NOT NULL;
ALTER TABLE gasto ADD COLUMN concepto VARCHAR(200);
ALTER TABLE gasto ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'UNICO';

UPDATE gasto g
SET concepto = t.nombre,
    origen = CASE WHEN lower(t.nombre) = 'comisiones' THEN 'COMISION' ELSE 'UNICO' END
FROM tipo_gasto t
WHERE t.id = g.tipo_gasto_id;

ALTER TABLE gasto ALTER COLUMN concepto SET NOT NULL;

-- Gastos recurrentes con vencimiento (renta, luz, agua, nómina...).
-- SEMANAL: cada 7 días a partir de primer_vencimiento; QUINCENAL: los días dia y dia2 de cada mes;
-- MENSUAL / BIMESTRAL / ANUAL: el día dia (el último del mes si es más corto) cada 1 / 2 / 12 meses a
-- partir del mes de primer_vencimiento.
CREATE TABLE gasto_recurrente (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    monto_estimado NUMERIC(14, 2) NOT NULL CHECK (monto_estimado >= 0),
    frecuencia VARCHAR(20) NOT NULL,
    dia INTEGER CHECK (dia BETWEEN 1 AND 31),
    dia2 INTEGER CHECK (dia2 BETWEEN 1 AND 31),
    primer_vencimiento DATE NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

-- Cada vencimiento de un gasto recurrente. Pagado = tiene gasto; omitido = se saltó ese periodo.
CREATE TABLE gasto_recurrente_pago (
    id BIGSERIAL PRIMARY KEY,
    recurrente_id BIGINT NOT NULL REFERENCES gasto_recurrente(id) ON DELETE CASCADE,
    fecha_vencimiento DATE NOT NULL,
    monto_estimado NUMERIC(14, 2) NOT NULL,
    omitido BOOLEAN NOT NULL DEFAULT FALSE,
    gasto_id BIGINT REFERENCES gasto(id),
    CONSTRAINT uk_gasto_recurrente_pago UNIQUE (recurrente_id, fecha_vencimiento)
);
CREATE INDEX idx_gasto_recurrente_pago_fecha ON gasto_recurrente_pago(fecha_vencimiento);
