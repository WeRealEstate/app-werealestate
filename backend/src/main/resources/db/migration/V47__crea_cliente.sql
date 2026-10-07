-- Clientes: se registran una vez y se ligan a todas sus ventas (un cliente de Aldea Nanuu que vuelve
-- por SAMAI conserva su ficha). Todo salvo nombre y apellido paterno es opcional a nivel base: los
-- clientes creados a partir de ventas anteriores quedan con "datos incompletos" hasta completarlos.
CREATE TABLE cliente (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    apellido_paterno VARCHAR(120),
    apellido_materno VARCHAR(120),
    fecha_nacimiento DATE,
    telefono VARCHAR(30),
    telefono2 VARCHAR(30),
    correo VARCHAR(150),
    curp VARCHAR(18),
    rfc VARCHAR(13),
    lugar_nacimiento VARCHAR(120),
    nacionalidad VARCHAR(80),
    estado_civil VARCHAR(20),
    ocupacion VARCHAR(120),
    calle VARCHAR(200),
    colonia VARCHAR(120),
    municipio VARCHAR(120),
    estado VARCHAR(80),
    codigo_postal VARCHAR(10),
    beneficiario_nombre VARCHAR(200),
    beneficiario_parentesco VARCHAR(80),
    fuente VARCHAR(30),
    fuente_detalle VARCHAR(200),
    captado_por_usuario_id BIGINT REFERENCES usuario(id),
    captado_por_asesor_id BIGINT REFERENCES asesor_externo(id),
    notas VARCHAR(1000),
    expediente_ubicacion VARCHAR(20),
    expediente_drive_url VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_cliente_curp ON cliente (upper(curp)) WHERE curp IS NOT NULL;

ALTER TABLE venta ADD COLUMN cliente_id BIGINT REFERENCES cliente(id);
CREATE INDEX idx_venta_cliente ON venta(cliente_id);
