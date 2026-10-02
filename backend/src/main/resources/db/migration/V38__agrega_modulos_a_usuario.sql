-- Módulos del panel que puede usar cada usuario (ver ModulosAcceso): lista separada por comas, o NULL
-- para "los de su rol por defecto". Admin siempre ve todo, así que se queda en NULL.
ALTER TABLE usuario ADD COLUMN modulos VARCHAR(200);

-- Los usuarios que ya existen conservan exactamente el acceso que tenían (el máximo de su rol): el
-- default nuevo de Administración (sin Leads) aplica solo a usuarios nuevos; a los actuales el admin
-- les quita lo que no necesiten desde Usuarios.
UPDATE usuario SET modulos = 'CALENDARIO,COTIZADOR,LEADS,LOTES,PIPELINE,PLANO' WHERE rol = 'ASESOR';
UPDATE usuario SET modulos = 'CALENDARIO,GASTOS,LEADS,LOTES,VENTAS' WHERE rol = 'LIDER_AREA';
UPDATE usuario SET modulos = 'CALENDARIO' WHERE rol = 'EQUIPO_INTERNO';
