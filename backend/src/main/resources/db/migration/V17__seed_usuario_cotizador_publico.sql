-- Usuario "de sistema" al que se atribuyen las cotizaciones generadas desde /cotizador-publico
-- (sin sesión iniciada). Queda inactivo a propósito: nunca puede iniciar sesión (Spring Security
-- rechaza a los usuarios con activo = FALSE antes de siquiera revisar la contraseña), solo existe
-- para satisfacer la referencia obligatoria de cotizacion.asesor_id y para que el admin identifique
-- en el historial cuáles cotizaciones vinieron del link público en vez de un asesor real.
INSERT INTO usuario (nombre, email, password, rol, area_id, activo)
VALUES (
    'Cotizador público (sin sesión)',
    'cotizador-publico@weinversiones.com',
    '$2b$10$FS3Z/16Z8Ke8f.32uGYY4.ldHD627LO5MDsGEbs7deac1RxPwqq2i',
    'ASESOR',
    NULL,
    FALSE
);
