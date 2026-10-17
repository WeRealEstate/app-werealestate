-- Material de venta (copys, flyers, brochures, videos…) que administración sube y los asesores consultan desde
-- el plano público. Solo crea una tabla nueva: no modifica ningún dato existente. Los archivos viven en la
-- carpeta privada (UPLOADS_PRIVADOS_DIR/material), no en la pública.
CREATE TABLE material (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion VARCHAR(1000),
    categoria VARCHAR(20) NOT NULL,
    proyecto VARCHAR(20) NOT NULL,
    texto TEXT,
    archivo_nombre VARCHAR(200),
    archivo_extension VARCHAR(10),
    archivo_tamano BIGINT,
    creado_por BIGINT REFERENCES usuario (id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_material_proyecto ON material (proyecto);
