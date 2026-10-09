-- Comunidades We: los usuarios internos con rol ASESOR también entran al árbol de equipos
-- (líder / línea 1 / línea 2). Cada uno tiene una "ficha de comunidad" en asesor_externo enlazada
-- a su usuario; el resto de pantallas de asesores externos la ignora (usuario_id NOT NULL).
ALTER TABLE asesor_externo ADD COLUMN usuario_id BIGINT REFERENCES usuario(id);
CREATE UNIQUE INDEX uq_asesor_externo_usuario ON asesor_externo (usuario_id) WHERE usuario_id IS NOT NULL;

INSERT INTO asesor_externo (nombre, correo, activo, usuario_id)
SELECT u.nombre, u.email, u.activo, u.id
FROM usuario u
WHERE u.rol = 'ASESOR'
  AND lower(u.email) <> 'cotizador-publico@weinversiones.com'
  AND NOT EXISTS (SELECT 1 FROM asesor_externo ae WHERE ae.usuario_id = u.id);
