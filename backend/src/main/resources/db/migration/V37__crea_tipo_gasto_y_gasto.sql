CREATE TABLE tipo_gasto (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    requiere_ticket BOOLEAN NOT NULL DEFAULT false,
    activo BOOLEAN NOT NULL DEFAULT true,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

-- Evita dos tipos de gasto con el mismo nombre (sin importar mayúsculas/minúsculas), que
-- confundiría al elegir uno al registrar un gasto.
CREATE UNIQUE INDEX idx_tipo_gasto_nombre ON tipo_gasto (lower(nombre));

CREATE TABLE gasto (
    id BIGSERIAL PRIMARY KEY,
    tipo_gasto_id BIGINT NOT NULL REFERENCES tipo_gasto(id),
    fecha DATE NOT NULL,
    monto NUMERIC(14,2) NOT NULL,
    -- Extensión del archivo del ticket ('jpg'/'png'/'webp'/'pdf'), null si el tipo de gasto no lo
    -- exige o no se subió ninguno. El archivo en sí NO vive bajo uploads/ (esa carpeta se sirve sin
    -- autenticación, ver SecurityConfig/WebConfig) sino en una carpeta aparte que solo el backend
    -- lee, y se entrega por un endpoint autenticado (ver GastoService/GastoController).
    ticket_extension VARCHAR(10),
    registrado_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_gasto_tipo_gasto ON gasto (tipo_gasto_id);
CREATE INDEX idx_gasto_fecha ON gasto (fecha);
