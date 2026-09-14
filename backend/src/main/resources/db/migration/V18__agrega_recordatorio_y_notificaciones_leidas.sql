ALTER TABLE evento_calendario ADD COLUMN recordatorio BOOLEAN NOT NULL DEFAULT FALSE;

-- Registra qué notificaciones "en vivo" (NotificacionService) ya vio cada usuario, para que no
-- vuelvan a aparecer aunque la condición que las generó siga vigente. (tipo, entidad_id, firma)
-- identifica una ocurrencia concreta: firma cambia cuando la condición se renueva de verdad
-- (ej. se contacta al lead y vuelve a enfriarse después, o se reprograma un seguimiento), así que
-- esas sí vuelven a notificar.
CREATE TABLE notificacion_leida (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL,
    entidad_id BIGINT NOT NULL,
    firma VARCHAR(100) NOT NULL,
    fecha_marcada TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_notificacion_leida UNIQUE (usuario_id, tipo, entidad_id, firma)
);

CREATE INDEX idx_notificacion_leida_usuario ON notificacion_leida(usuario_id);
