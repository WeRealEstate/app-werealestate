-- Módulo nuevo "Registrar venta" (REGISTRAR_VENTA, ver ModulosAcceso): los asesores lo traen de entrada. Los que ya
-- tienen su lista de módulos guardada no lo verían sin esto; solo se AGREGA a esa lista (NULL = "los de su rol", que ya
-- lo incluye, no se toca). El admin se lo puede quitar después desde Usuarios.
UPDATE usuario
SET modulos = CASE WHEN modulos = '' THEN 'REGISTRAR_VENTA' ELSE modulos || ',REGISTRAR_VENTA' END
WHERE rol = 'ASESOR'
  AND modulos IS NOT NULL
  AND (',' || modulos || ',') NOT LIKE '%,REGISTRAR_VENTA,%';
