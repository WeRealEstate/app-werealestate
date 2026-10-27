-- Módulo nuevo "Score de ventas" (SCORE, ver ModulosAcceso): Administración lo trae de entrada. Un usuario nuevo
-- lo recibe con el default de su rol, pero los de Administración que ya existen tienen su lista de módulos
-- guardada (V38) y sin esto no lo verían. Aquí solo se AGREGA 'SCORE' a esa lista: no se quita ni se cambia ningún
-- otro módulo, y NULL ("los de su rol por defecto", que ya lo incluye) no se toca. El admin se lo puede quitar
-- después a quien quiera desde Usuarios.
UPDATE usuario
SET modulos = CASE WHEN modulos = '' THEN 'SCORE' ELSE modulos || ',SCORE' END
WHERE rol = 'LIDER_AREA'
  AND modulos IS NOT NULL
  AND (',' || modulos || ',') NOT LIKE '%,SCORE,%';
