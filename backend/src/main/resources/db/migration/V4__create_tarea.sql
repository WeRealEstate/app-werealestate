CREATE TABLE tarea (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    asignado_a_id BIGINT NOT NULL REFERENCES usuario(id),
    creado_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_limite DATE,
    completada BOOLEAN NOT NULL DEFAULT false,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tarea_asignado_a ON tarea(asignado_a_id);
CREATE INDEX idx_tarea_creado_por ON tarea(creado_por_id);
