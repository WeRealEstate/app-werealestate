-- Nómina semanal por usuario (opcional): cada sábado genera un pago pendiente en Gastos. Se guarda en
-- el usuario (monto y desde cuándo) y se refleja como un gasto recurrente SEMANAL ligado a él
-- (gasto_recurrente.usuario_id), que solo se administra desde Usuarios.
ALTER TABLE usuario ADD COLUMN nomina_semanal NUMERIC(14, 2) CHECK (nomina_semanal IS NULL OR nomina_semanal > 0);
ALTER TABLE usuario ADD COLUMN nomina_desde DATE;

ALTER TABLE gasto_recurrente ADD COLUMN usuario_id BIGINT REFERENCES usuario(id);
CREATE UNIQUE INDEX uk_gasto_recurrente_usuario ON gasto_recurrente(usuario_id) WHERE usuario_id IS NOT NULL;
