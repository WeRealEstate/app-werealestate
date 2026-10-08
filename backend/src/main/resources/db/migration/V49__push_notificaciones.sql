-- Notificaciones push del teléfono (PWA): suscripciones por dispositivo, lo ya enviado (para no repetir)
-- y las llaves VAPID del servidor (se generan solas la primera vez).
CREATE TABLE push_suscripcion (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh VARCHAR(200) NOT NULL,
    auth VARCHAR(100) NOT NULL,
    user_agent VARCHAR(300),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_suscripcion_usuario ON push_suscripcion (usuario_id);

CREATE TABLE push_enviada (
    usuario_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    clave VARCHAR(300) NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, clave)
);

CREATE TABLE push_config (
    id INT PRIMARY KEY CHECK (id = 1),
    public_key VARCHAR(200) NOT NULL,
    private_key VARCHAR(200) NOT NULL
);
