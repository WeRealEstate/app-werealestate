-- Quién registró la venta: para avisar a admin y Administración sin avisarle a quien la capturó.
ALTER TABLE venta ADD COLUMN registrada_por_id BIGINT REFERENCES usuario (id) ON DELETE SET NULL;
