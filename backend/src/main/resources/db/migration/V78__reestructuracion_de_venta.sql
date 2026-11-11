-- Reestructuración del plan de pagos de una venta (p. ej. de mensualidades a anualidades). Lo pagado hasta la fecha
-- de corte se queda como estaba y el plan nuevo reparte solo el saldo. Cada reestructuración guarda el plan que
-- reemplazó (para seguir mostrando sus pagos en la tabla de amortización) y lo que se había abonado al corte.
-- Solo agrega tablas y columnas: no toca datos existentes.
CREATE TABLE venta_reestructuracion (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    fecha_corte DATE NOT NULL,
    abonado_al_corte NUMERIC(14, 2) NOT NULL,
    saldo_al_corte NUMERIC(14, 2) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    usuario_id BIGINT REFERENCES usuario(id),
    fecha_registro TIMESTAMP NOT NULL DEFAULT now(),
    -- El plan que se reemplazó.
    ant_fecha_inicio DATE NOT NULL,
    ant_forma_pago VARCHAR(50) NOT NULL,
    ant_mensualidad NUMERIC(14, 2),
    ant_plazo_meses INTEGER,
    ant_dia_pago INTEGER NOT NULL,
    ant_primera_mes_inicio BOOLEAN NOT NULL,
    ant_enganche_label VARCHAR(30),
    ant_enganche NUMERIC(14, 2),
    resumen_anterior VARCHAR(300) NOT NULL,
    resumen_nuevo VARCHAR(300) NOT NULL
);

CREATE INDEX idx_venta_reestructuracion_venta ON venta_reestructuracion(venta_id);

-- Aportaciones del plan que creó una reestructuración (null = las del plan original de la venta).
ALTER TABLE venta_aportacion ADD COLUMN reestructuracion_id BIGINT REFERENCES venta_reestructuracion(id);

-- Desde cuándo corre el plan vigente de la venta (null = desde la fecha de venta, como siempre).
ALTER TABLE venta ADD COLUMN inicio_plan DATE;
