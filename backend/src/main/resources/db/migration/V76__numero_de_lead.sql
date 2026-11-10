-- Número consecutivo de cada lead dentro de su asesor (1, 2, 3…). Los leads que ya existen se numeran por fecha de
-- creación; los nuevos toman el siguiente del asesor (y al reasignar, el siguiente del asesor que lo recibe).
-- Aditiva: solo agrega una columna y un índice.
ALTER TABLE lead ADD COLUMN numero BIGINT;

UPDATE lead
SET numero = r.n
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY asesor_id ORDER BY fecha_creacion, id) AS n
    FROM lead
) r
WHERE lead.id = r.id;

ALTER TABLE lead ALTER COLUMN numero SET NOT NULL;
CREATE UNIQUE INDEX ux_lead_asesor_numero ON lead (asesor_id, numero);
