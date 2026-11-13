ALTER TABLE venta
    ADD COLUMN sin_comision BOOLEAN NOT NULL DEFAULT FALSE;

-- Si la venta sigue activa y su comisión principal estaba cancelada (y sin pagos/devoluciones),
-- marcamos la venta para que no se regenere automáticamente al arrancar o al registrar pagos.
UPDATE venta v
SET sin_comision = TRUE
WHERE EXISTS (
    SELECT 1
    FROM venta_comision vc
    WHERE vc.venta_id = v.id
      AND vc.secundaria = FALSE
      AND vc.cancelada = TRUE
      AND NOT EXISTS (SELECT 1 FROM venta_comision_entrega e WHERE e.comision_id = vc.id)
      AND NOT EXISTS (SELECT 1 FROM venta_comision_devolucion d WHERE d.comision_id = vc.id)
);

-- Eliminar permanentemente las comisiones ya canceladas que no tienen pagos ni devoluciones.
-- Los devengos asociados tienen ON DELETE CASCADE.
DELETE FROM venta_comision vc
WHERE vc.cancelada = TRUE
  AND NOT EXISTS (SELECT 1 FROM venta_comision_entrega e WHERE e.comision_id = vc.id)
  AND NOT EXISTS (SELECT 1 FROM venta_comision_devolucion d WHERE d.comision_id = vc.id);
