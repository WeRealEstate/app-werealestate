-- Bono de la promoción por varios lotes al comprar un solo lote (decisión del usuario, 2026-11-13).
-- Columna nueva y opcional: NULL = la promoción no aplica a 1 lote. No cambia ningún dato.
ALTER TABLE promocion ADD COLUMN bono_un_lote NUMERIC(14,2);
