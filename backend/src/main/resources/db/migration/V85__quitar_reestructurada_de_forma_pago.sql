UPDATE venta
SET forma_pago = CASE
    WHEN forma_pago LIKE 'Reestructurada · con enganche · %' THEN 'Con enganche · ' || SUBSTRING(forma_pago FROM 33)
    WHEN forma_pago LIKE 'Reestructurada · %' THEN SUBSTRING(forma_pago FROM 18)
    ELSE forma_pago
END
WHERE forma_pago LIKE 'Reestructurada · %';
