-- Una aportación por mes en cada plan de la venta, no en toda la venta: al reestructurar, el plan nuevo puede tener
-- una aportación en el mismo mes que el plan reemplazado (cuyas aportaciones se conservan para los recibos que ya las
-- pagaron). Con la regla anterior (venta, año, mes) la reestructuración fallaba. No toca datos: solo cambia la regla
-- de unicidad por una más amplia que todas las filas existentes ya cumplen.
ALTER TABLE venta_aportacion DROP CONSTRAINT uk_venta_aportacion;
CREATE UNIQUE INDEX uk_venta_aportacion_plan
    ON venta_aportacion (venta_id, anio, mes, COALESCE(reestructuracion_id, 0));
