-- Los grupos existentes conservan su categoría; no se modifican líderes, integrantes ni enlaces.
ALTER TABLE comercializadora ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'COMERCIALIZADORA';
ALTER TABLE comercializadora ADD CONSTRAINT chk_comercializadora_tipo CHECK (tipo IN ('COMERCIALIZADORA', 'EQUIPO'));
