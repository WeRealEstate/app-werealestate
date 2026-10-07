-- Ficha del asesor externo: datos secundarios que se consultan de vez en cuando. Todo opcional (NULL =
-- "sin definir"): experiencia, dónde está la copia del contrato y del expediente (físico / Drive, con
-- link a Drive), quién trajo al asesor (usuario del sistema, otro asesor externo, u otro: un nombre
-- o "captado en un evento") y notas internas.
ALTER TABLE asesor_externo
    ADD COLUMN experiencia VARCHAR(20),
    ADD COLUMN contrato_copia VARCHAR(20),
    ADD COLUMN contrato_drive_url VARCHAR(500),
    ADD COLUMN expediente_aplica BOOLEAN,
    ADD COLUMN expediente_ubicacion VARCHAR(20),
    ADD COLUMN expediente_drive_url VARCHAR(500),
    ADD COLUMN traido_por_usuario_id BIGINT REFERENCES usuario(id),
    ADD COLUMN traido_por_asesor_id BIGINT REFERENCES asesor_externo(id),
    ADD COLUMN traido_por_otro VARCHAR(200),
    ADD COLUMN notas VARCHAR(1000);
