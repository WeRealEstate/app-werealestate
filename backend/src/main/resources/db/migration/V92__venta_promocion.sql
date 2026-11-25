-- Promoción con la que se vendió (nombre y bono tal como eran al registrar la venta; sin llave a promocion, que se puede borrar).
ALTER TABLE venta ADD COLUMN promocion_nombre VARCHAR(150);
ALTER TABLE venta ADD COLUMN promocion_bono NUMERIC(14,2);
