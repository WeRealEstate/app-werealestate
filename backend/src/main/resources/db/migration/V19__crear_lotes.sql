CREATE TABLE lote (
    id BIGSERIAL PRIMARY KEY,
    desarrollo_id BIGINT NOT NULL REFERENCES desarrollo(id),
    manzana VARCHAR(20) NOT NULL,
    numero_lote VARCHAR(20) NOT NULL,
    superficie NUMERIC(10, 4) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'DISPONIBLE',
    fecha_cambio_estado TIMESTAMP NOT NULL DEFAULT now(),
    cambiado_por_id BIGINT REFERENCES usuario(id),
    CONSTRAINT uk_lote_ubicacion UNIQUE (desarrollo_id, manzana, numero_lote)
);

CREATE INDEX idx_lote_desarrollo ON lote(desarrollo_id);
CREATE INDEX idx_lote_estado_fecha ON lote(estado, fecha_cambio_estado);
