-- Archivos de Registrar venta (We Forms) en Google Drive. La cuenta se conecta una vez desde el panel (OAuth con el
-- permiso drive.file: la app solo ve lo que ella crea y la carpeta que el admin elige con el selector de Google) y un
-- proceso en segundo plano sube cada archivo a la carpeta de su cliente, dentro de esa carpeta elegida; ya
-- confirmado por Google, se borra del servidor.
-- Solo agrega una tabla y columnas nullable (más un contador con default): no modifica ningún dato existente.

-- Una sola fila (id = 1). El refresh token por sí solo no sirve: para usarlo hacen falta el client id y el secret,
-- que viven en variables de entorno del servidor, no en la base de datos.
CREATE TABLE drive_config (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    refresh_token TEXT,
    cuenta_email VARCHAR(200),
    conectado_en TIMESTAMP,
    -- La carpeta que eligió el admin (donde se crean las de los clientes).
    carpeta_id VARCHAR(100),
    carpeta_nombre VARCHAR(300),
    ultimo_error VARCHAR(500),
    ultimo_error_en TIMESTAMP
);

-- Carpeta del cliente en Drive (creada por la app).
ALTER TABLE cliente ADD COLUMN drive_carpeta_id VARCHAR(100);

-- Estado de la subida de cada archivo de un registro de venta.
ALTER TABLE we_form_respuesta_item
    ADD COLUMN drive_archivo_id VARCHAR(100),
    ADD COLUMN drive_subido_en TIMESTAMP,
    ADD COLUMN drive_intentos INT NOT NULL DEFAULT 0,
    ADD COLUMN drive_ultimo_intento TIMESTAMP,
    ADD COLUMN drive_error VARCHAR(500);

-- Para encontrar rápido los pendientes (archivo sin subir).
CREATE INDEX idx_we_form_respuesta_item_drive_pendiente
    ON we_form_respuesta_item (id)
    WHERE archivo_extension IS NOT NULL AND drive_archivo_id IS NULL;
