-- El cotizador ahora puede combinar varios lotes en una sola cotización (cliente que compra más
-- de uno en la misma operación). manzana/lote pasan a guardar la lista completa separada por
-- comas (ej. "5, 5, 6" / "11, 12, 3") en vez de un solo par; VARCHAR(30) se quedaba corto para eso.
ALTER TABLE cotizacion ALTER COLUMN manzana TYPE VARCHAR(500);
ALTER TABLE cotizacion ALTER COLUMN lote TYPE VARCHAR(500);
