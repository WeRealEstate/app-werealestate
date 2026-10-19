-- Galerías de imágenes del Material de venta: un material de categoría GALERIA agrupa varias imágenes (renders,
-- flyers, fotos del proyecto) en una sola tarjeta. Solo crea una tabla nueva: no modifica ningún dato existente
-- (la categoría GALERIA cabe en material.categoria, que es texto). Los archivos viven en la carpeta privada
-- (UPLOADS_PRIVADOS_DIR/material/galeria-{material_id}), no en la pública.
CREATE TABLE material_imagen (
    id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL REFERENCES material (id) ON DELETE CASCADE,
    orden INT NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    extension VARCHAR(10) NOT NULL,
    tamano BIGINT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_material_imagen_material ON material_imagen (material_id, orden);
