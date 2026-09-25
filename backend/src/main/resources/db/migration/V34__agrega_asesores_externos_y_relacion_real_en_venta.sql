-- Asesores externos: personas que venden pero no tienen cuenta en el sistema. Antes el asesor de
-- una venta era texto libre (ver Venta.asesor / V13); ahora es una relación real a un usuario
-- interno o a uno de estos, no texto suelto.
CREATE TABLE asesor_externo (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE venta ADD COLUMN usuario_asesor_id BIGINT REFERENCES usuario(id);
ALTER TABLE venta ADD COLUMN asesor_externo_id BIGINT REFERENCES asesor_externo(id);

-- Backfill de ventas ya capturadas (si las hay): si el texto libre coincide con el nombre de un
-- usuario real, se enlaza a ese usuario; si no, se crea un asesor externo con ese mismo nombre
-- para no perder el dato. No hay forma de saber "quién es quién" mejor que por nombre, ya que el
-- texto nunca tuvo una relación real detrás.
INSERT INTO asesor_externo (nombre)
SELECT DISTINCT trim(v.asesor)
FROM venta v
WHERE NOT EXISTS (
    SELECT 1 FROM usuario u WHERE lower(trim(u.nombre)) = lower(trim(v.asesor))
)
AND NOT EXISTS (
    SELECT 1 FROM asesor_externo ae WHERE lower(trim(ae.nombre)) = lower(trim(v.asesor))
);

UPDATE venta v
SET usuario_asesor_id = u.id
FROM usuario u
WHERE lower(trim(u.nombre)) = lower(trim(v.asesor));

UPDATE venta v
SET asesor_externo_id = ae.id
FROM asesor_externo ae
WHERE v.usuario_asesor_id IS NULL
  AND lower(trim(ae.nombre)) = lower(trim(v.asesor));

ALTER TABLE venta DROP COLUMN asesor;

-- Exactamente uno de los dos debe estar lleno: nunca los dos, nunca ninguno.
ALTER TABLE venta ADD CONSTRAINT chk_venta_asesor_uno CHECK (
    (usuario_asesor_id IS NOT NULL AND asesor_externo_id IS NULL)
    OR (usuario_asesor_id IS NULL AND asesor_externo_id IS NOT NULL)
);

CREATE INDEX idx_venta_usuario_asesor ON venta(usuario_asesor_id);
CREATE INDEX idx_venta_asesor_externo ON venta(asesor_externo_id);
