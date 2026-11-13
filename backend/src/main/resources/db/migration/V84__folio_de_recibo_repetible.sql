-- Folios repetibles (decisión del usuario, 2026-11-13): el folio de un recibo sigue siendo obligatorio para los abonos
-- nuevos (lo exige la API), pero ya puede repetirse entre recibos. Solo quita la regla de unicidad (índice de V56/V57);
-- no borra ni cambia ningún dato.
DROP INDEX IF EXISTS uq_recibo_venta_folio;
