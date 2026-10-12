-- Comisión del copropietario de la venta (un segundo asesor): una venta puede tener ahora dos filas
-- en venta_comision, la del asesor principal y la del segundo asesor (secundaria = TRUE).
--
-- Migración sin pérdida de datos: agrega una columna (todas las comisiones existentes quedan como
-- principales, secundaria = FALSE) y reemplaza el índice único "una comisión por venta" por "una
-- principal y una secundaria por venta"; lo que ya existe sigue cumpliéndolo.
ALTER TABLE venta_comision ADD COLUMN secundaria BOOLEAN NOT NULL DEFAULT FALSE;
DROP INDEX uk_venta_comision_venta;
CREATE UNIQUE INDEX uk_venta_comision_venta ON venta_comision (venta_id, secundaria) WHERE venta_id IS NOT NULL;
