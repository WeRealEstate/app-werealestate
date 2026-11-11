-- Una reestructuración "completa" rehace todo el plan de pagos desde el primer mes de la venta y reparte el precio
-- completo (los abonos ya registrados se vuelven a aplicar en orden); las anteriores (false) solo repartían el saldo
-- desde la fecha de corte. Solo agrega una columna: las reestructuraciones existentes conservan su comportamiento.
ALTER TABLE venta_reestructuracion ADD COLUMN completa BOOLEAN NOT NULL DEFAULT FALSE;
