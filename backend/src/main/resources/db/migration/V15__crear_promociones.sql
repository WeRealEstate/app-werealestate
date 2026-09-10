CREATE TABLE promocion (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    proyecto VARCHAR(20) NOT NULL,
    mensualidad_fija NUMERIC(14,2) NOT NULL,
    descripcion VARCHAR(255),
    activa BOOLEAN NOT NULL DEFAULT true,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_promocion_proyecto_activa ON promocion(proyecto, activa);
