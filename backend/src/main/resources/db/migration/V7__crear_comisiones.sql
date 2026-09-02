CREATE TABLE configuracion_comision (
    id BIGINT PRIMARY KEY,
    porcentaje NUMERIC(5,2) NOT NULL
);

INSERT INTO configuracion_comision (id, porcentaje) VALUES (1, 5.00);

CREATE TABLE comision (
    id BIGSERIAL PRIMARY KEY,
    lead_id BIGINT NOT NULL REFERENCES lead(id),
    asesor_id BIGINT NOT NULL REFERENCES usuario(id),
    monto NUMERIC(14,2) NOT NULL,
    porcentaje_aplicado NUMERIC(5,2) NOT NULL,
    pagada BOOLEAN NOT NULL DEFAULT false,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    fecha_pago TIMESTAMP
);

-- A lo más una comisión por lead: se genera una sola vez, la primera vez que cierra ganado.
CREATE UNIQUE INDEX idx_comision_lead ON comision(lead_id);
CREATE INDEX idx_comision_asesor ON comision(asesor_id);
