CREATE TABLE etiqueta (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL,
    color VARCHAR(20) NOT NULL,
    asesor_id BIGINT NOT NULL REFERENCES usuario(id)
);

CREATE TABLE lead_etiqueta (
    lead_id BIGINT NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    etiqueta_id BIGINT NOT NULL REFERENCES etiqueta(id) ON DELETE CASCADE,
    PRIMARY KEY (lead_id, etiqueta_id)
);
