ALTER TABLE promocion
    ADD COLUMN modalidad VARCHAR(30) NOT NULL DEFAULT 'MENSUALIDAD_FIJA',
    ADD COLUMN bono_dos_lotes NUMERIC(14,2),
    ADD COLUMN bono_tres_lotes NUMERIC(14,2),
    ADD COLUMN plazo_fijo INTEGER;

ALTER TABLE promocion
    ALTER COLUMN mensualidad_fija DROP NOT NULL;
