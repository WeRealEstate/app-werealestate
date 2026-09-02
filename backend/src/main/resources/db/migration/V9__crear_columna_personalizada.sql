CREATE TABLE columna_personalizada (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL,
    asesor_id BIGINT NOT NULL REFERENCES usuario(id),
    orden INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE lead ADD COLUMN columna_personalizada_id BIGINT REFERENCES columna_personalizada(id) ON DELETE SET NULL;
