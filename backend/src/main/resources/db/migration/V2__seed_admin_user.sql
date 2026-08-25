-- Usuario admin de prueba para poder probar el login antes de tener una pantalla de registro.
-- Contraseña en texto plano: Admin123!
INSERT INTO usuario (nombre, email, password, rol, area_id, activo)
VALUES (
    'Administrador We',
    'admin@weinversiones.com',
    '$2b$10$F4YRl4CIAwETIevHn7aScOv6hRoLpX3QAF3CSuq0xPYCI458TNJSe',
    'ADMIN',
    NULL,
    TRUE
);
