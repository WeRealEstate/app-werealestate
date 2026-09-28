-- Teams: un asesor externo ahora puede ser INDEPENDIENTE (como hasta ahora, default para todos
-- los ya registrados), LIDER de un equipo, o LINEA (miembro de un equipo, reportando a un LIDER
-- en línea 1 o a otro LINEA de línea 1 en línea 2 — tope de 3 niveles: Líder → Línea 1 → Línea 2).
ALTER TABLE asesor_externo ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'INDEPENDIENTE';
ALTER TABLE asesor_externo ADD COLUMN lider_directo_id BIGINT REFERENCES asesor_externo(id);
ALTER TABLE asesor_externo ADD COLUMN nivel_linea INTEGER;

ALTER TABLE asesor_externo ADD CONSTRAINT chk_asesor_externo_tipo
    CHECK (tipo IN ('INDEPENDIENTE', 'LIDER', 'LINEA'));

ALTER TABLE asesor_externo ADD CONSTRAINT chk_asesor_externo_nivel_linea
    CHECK (nivel_linea IS NULL OR nivel_linea IN (1, 2));

-- Solo un LINEA tiene líder directo y nivel de línea; INDEPENDIENTE y LIDER no reportan a nadie.
ALTER TABLE asesor_externo ADD CONSTRAINT chk_asesor_externo_jerarquia CHECK (
    (tipo = 'LINEA' AND lider_directo_id IS NOT NULL AND nivel_linea IS NOT NULL)
    OR (tipo != 'LINEA' AND lider_directo_id IS NULL AND nivel_linea IS NULL)
);

CREATE INDEX idx_asesor_externo_lider_directo ON asesor_externo(lider_directo_id);
