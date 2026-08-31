CREATE TABLE desarrollo (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    ubicacion VARCHAR(200) NOT NULL,
    precio_m2 NUMERIC(12, 2) NOT NULL,
    area_minima NUMERIC(10, 2) NOT NULL
);

INSERT INTO desarrollo (nombre, ubicacion, precio_m2, area_minima) VALUES
    ('SAMAI Campestre', 'San José La Ciénega, Pochutla, Oaxaca', 800.00, 200.00),
    ('Aldea Nanuu', 'Cuatunalco, Santa María Huatulco, Oaxaca', 3700.00, 200.00);

CREATE TABLE lead (
    id BIGSERIAL PRIMARY KEY,
    nombre_cliente VARCHAR(150) NOT NULL,
    telefono VARCHAR(30) NOT NULL,
    email VARCHAR(150),
    origen VARCHAR(100),
    desarrollo_id BIGINT NOT NULL REFERENCES desarrollo(id),
    asesor_id BIGINT NOT NULL REFERENCES usuario(id),
    estado VARCHAR(30) NOT NULL DEFAULT 'NUEVO',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    fecha_ultimo_contacto TIMESTAMP NOT NULL DEFAULT now(),
    valor_estimado NUMERIC(14, 2)
);

CREATE INDEX idx_lead_asesor ON lead(asesor_id);
CREATE INDEX idx_lead_estado ON lead(estado);

CREATE TABLE seguimiento (
    id BIGSERIAL PRIMARY KEY,
    lead_id BIGINT NOT NULL REFERENCES lead(id),
    asesor_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha TIMESTAMP NOT NULL DEFAULT now(),
    tipo VARCHAR(20) NOT NULL,
    nota TEXT NOT NULL,
    resultado VARCHAR(200),
    proximo_seguimiento TIMESTAMP
);

CREATE INDEX idx_seguimiento_lead ON seguimiento(lead_id);
