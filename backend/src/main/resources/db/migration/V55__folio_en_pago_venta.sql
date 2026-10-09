-- Folio del abono, capturado a mano al registrarlo. Obligatorio para los abonos nuevos (lo exige la
-- API), por eso la columna admite NULL: los abonos anteriores no tienen folio. Único sin importar
-- mayúsculas ni espacios en los extremos.
ALTER TABLE pago_venta ADD COLUMN folio VARCHAR(50);
CREATE UNIQUE INDEX uq_pago_venta_folio ON pago_venta (lower(btrim(folio))) WHERE folio IS NOT NULL;
