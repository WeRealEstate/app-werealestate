-- Comercializadoras: empresas que venden con su propio equipo. Cada una tiene una líder (un asesor externo de tipo
-- LIDER en Comunidades We) y un enlace secreto (token) que la líder comparte para que cada integrante de su equipo
-- se dé de alta solo. Solo agrega una tabla nueva y una columna nullable: no modifica ningún dato existente.
CREATE TABLE comercializadora (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    lider_id BIGINT NOT NULL REFERENCES asesor_externo (id),
    notas VARCHAR(1000),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    token VARCHAR(64) NOT NULL UNIQUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_comercializadora_lider ON comercializadora (lider_id);

ALTER TABLE asesor_externo ADD COLUMN comercializadora_id BIGINT REFERENCES comercializadora (id);
CREATE INDEX idx_asesor_externo_comercializadora ON asesor_externo (comercializadora_id);
