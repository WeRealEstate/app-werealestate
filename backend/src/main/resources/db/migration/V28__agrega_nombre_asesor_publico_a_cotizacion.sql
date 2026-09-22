-- El cotizador público (sin sesión) atribuye sus cotizaciones al usuario de sistema; este campo
-- guarda el nombre real del asesor que atendió al cliente con ese link (mismo dato que ya se
-- captura para el PDF, ver CotizadorComponent.advisorName), para que el historial no muestre
-- solo "Cotizador público (sin sesión)".
ALTER TABLE cotizacion ADD COLUMN nombre_asesor_publico VARCHAR(200);
