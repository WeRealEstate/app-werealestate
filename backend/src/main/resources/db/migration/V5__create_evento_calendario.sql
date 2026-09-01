CREATE TABLE evento_calendario (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    fecha DATE NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_evento_calendario_usuario ON evento_calendario(usuario_id);
CREATE INDEX idx_evento_calendario_fecha ON evento_calendario(fecha);
