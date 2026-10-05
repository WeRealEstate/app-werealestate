-- Número de venta visible (la más antigua por fecha de venta es la 1), guardado en la tabla para poder
-- buscarlo, mostrarlo en la dirección (/panel/ventas/11) y usarlo en reportes. Lo reacomoda
-- VentaService cada vez que se registra una venta o cambia su fecha (ver VentaRepository.renumerar).
ALTER TABLE venta ADD COLUMN numero BIGINT;

UPDATE venta v
SET numero = r.n
FROM (SELECT id, row_number() OVER (ORDER BY fecha_venta ASC, id ASC) AS n FROM venta) r
WHERE v.id = r.id;

-- Una venta nueva nace con un número provisional muy alto de esta secuencia (nunca choca con los
-- reales); enseguida VentaService reacomoda todos en su lugar según la fecha.
CREATE SEQUENCE venta_numero_provisional_seq START 1000000;
ALTER TABLE venta ALTER COLUMN numero SET DEFAULT nextval('venta_numero_provisional_seq');
ALTER TABLE venta ALTER COLUMN numero SET NOT NULL;

-- DEFERRABLE: al reacomodar varias filas en un solo UPDATE, el índice único se revisa al final de la
-- transacción y no fila por fila (si no, intercambiar dos números fallaría a la mitad).
ALTER TABLE venta ADD CONSTRAINT uk_venta_numero UNIQUE (numero) DEFERRABLE INITIALLY DEFERRED;
