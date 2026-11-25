-- Correos automáticos (bienvenida, liquidación y recordatorio de pago). Solo agrega columnas y tablas: no toca datos.
--
-- correos_automaticos: a las ventas que ya existen se les queda en false (la bienvenida y la liquidación solo salen
-- solas en ventas registradas de aquí en adelante; la aplicación pone true al registrar una venta).
ALTER TABLE venta ADD COLUMN correos_automaticos BOOLEAN NOT NULL DEFAULT FALSE;
-- Ventas a las que no se les manda el recordatorio de pago automático.
ALTER TABLE venta ADD COLUMN sin_recordatorio_automatico BOOLEAN NOT NULL DEFAULT FALSE;

-- Un interruptor por correo. Sin fila = apagado.
CREATE TABLE correo_automatico_config (
    tipo VARCHAR(20) PRIMARY KEY,
    activo BOOLEAN NOT NULL,
    cambiado_por BIGINT REFERENCES usuario (id),
    cambiado_en TIMESTAMP NOT NULL
);

-- Lo que el programador decidió para cada venta y correo: cola de envío y registro de lo que no salió. Lo enviado se
-- guarda además en las tablas de siempre (bienvenida_cliente, liquidacion_aviso, recordatorio_pago) sin usuario.
CREATE TABLE correo_automatico (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta (id) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_pago DATE,
    enviar_desde TIMESTAMP NOT NULL,
    creado_en TIMESTAMP NOT NULL,
    resuelto_en TIMESTAMP,
    intentos INTEGER NOT NULL DEFAULT 0,
    detalle VARCHAR(500)
);
CREATE UNIQUE INDEX uq_correo_automatico ON correo_automatico (venta_id, tipo, COALESCE(fecha_pago, DATE '1970-01-01'));
CREATE INDEX idx_correo_automatico_pendiente ON correo_automatico (estado) WHERE estado = 'PENDIENTE';
