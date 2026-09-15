CREATE TABLE movimiento_lote (
    id BIGSERIAL PRIMARY KEY,
    lote_id BIGINT NOT NULL REFERENCES lote(id) ON DELETE CASCADE,
    estado_anterior VARCHAR(30) NOT NULL,
    estado_nuevo VARCHAR(30) NOT NULL,
    usuario_id BIGINT REFERENCES usuario(id),
    nombre_asesor VARCHAR(150),
    fecha TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_movimiento_lote_lote ON movimiento_lote(lote_id, fecha DESC);
