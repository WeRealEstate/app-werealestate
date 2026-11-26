-- Pregunta propia de We Forms vinculada a un dato opcional del cliente (12.19.0): nacionalidad, lugar de nacimiento,
-- estado civil, ocupación o beneficiario. null = no alimenta al cliente (como hasta ahora).
ALTER TABLE we_form_pregunta ADD COLUMN campo_cliente VARCHAR(40);
