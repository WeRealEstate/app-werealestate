-- We Forms: formularios (precontratos) con link público. Cada formulario pertenece a un desarrollo;
-- sus preguntas viven en we_form_pregunta (las que alimentan al cliente y a la venta llevan un
-- "destino"). Las respuestas guardan la pregunta TAL COMO se contestó (título y tipo), así que editar
-- el formulario después no altera lo ya recibido. Nada se borra: los formularios se cierran y las
-- preguntas propias se archivan.
CREATE TABLE we_form (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    descripcion VARCHAR(1000),
    slug VARCHAR(40) NOT NULL UNIQUE,
    desarrollo_id BIGINT NOT NULL REFERENCES desarrollo (id),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    creado_por BIGINT REFERENCES usuario (id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE we_form_pregunta (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES we_form (id),
    orden INTEGER NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(300) NOT NULL,
    descripcion VARCHAR(500),
    obligatoria BOOLEAN NOT NULL DEFAULT FALSE,
    -- Opciones (una por línea) de las preguntas de opción múltiple, casillas y lista desplegable.
    opciones TEXT,
    -- Campo del cliente o de la venta que alimenta (null = pregunta propia del formulario).
    destino VARCHAR(40),
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    archivada BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_we_form_pregunta_form ON we_form_pregunta (form_id, orden);

CREATE TABLE we_form_respuesta (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES we_form (id),
    asesor_usuario_id BIGINT REFERENCES usuario (id),
    asesor_externo_id BIGINT REFERENCES asesor_externo (id),
    asesor_nombre VARCHAR(200) NOT NULL,
    cliente_id BIGINT REFERENCES cliente (id),
    cliente_nuevo BOOLEAN NOT NULL DEFAULT TRUE,
    venta_id BIGINT REFERENCES venta (id),
    fecha_envio TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_we_form_respuesta_form ON we_form_respuesta (form_id, fecha_envio DESC);

CREATE TABLE we_form_respuesta_item (
    id BIGSERIAL PRIMARY KEY,
    respuesta_id BIGINT NOT NULL REFERENCES we_form_respuesta (id),
    pregunta_id BIGINT REFERENCES we_form_pregunta (id),
    pregunta_titulo VARCHAR(300) NOT NULL,
    pregunta_tipo VARCHAR(30) NOT NULL,
    valor TEXT,
    archivo_extension VARCHAR(10),
    orden INTEGER NOT NULL
);
CREATE INDEX idx_we_form_respuesta_item_respuesta ON we_form_respuesta_item (respuesta_id, orden);

-- La venta que nace de un precontrato lleva la fecha del día de envío, provisional hasta que
-- administración la confirme (al firmar).
ALTER TABLE venta ADD COLUMN fecha_provisional BOOLEAN NOT NULL DEFAULT FALSE;
-- Dinero con el que ya estaba apartado un lote cuando se creó la venta desde un precontrato: queda
-- "pendiente de aplicar" hasta que administración decida si baja la mensualidad o el saldo.
ALTER TABLE venta ADD COLUMN deposito_pendiente NUMERIC(14, 2);
