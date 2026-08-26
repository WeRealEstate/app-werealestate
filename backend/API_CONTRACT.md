# Contrato de API — Autenticación (Fase 2)

El frontend (Angular) ya está listo y espera este endpoint exacto para poder conectarse.
Documentado aquí para implementarlo en Spring Boot sin ambigüedad.

## POST `/api/auth/login`

### Request

```json
{
  "email": "asesor@weinversiones.com",
  "password": "contraseña-en-texto-plano"
}
```

### Response — 200 OK

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "nombre": "Juan Pérez",
    "email": "asesor@weinversiones.com",
    "rol": "ASESOR",
    "areaId": null
  }
}
```

- `rol` debe ser exactamente uno de: `"ASESOR"`, `"LIDER_AREA"`, `"EQUIPO_INTERNO"`, `"ADMIN"` (mayúsculas, con guión bajo). El frontend usa este valor literal para decidir a qué panel redirigir — cualquier otro string no coincidirá con nada y el usuario se quedará sin panel.
- `areaId` es `null` si el usuario no pertenece a un área (por ejemplo, un asesor sin líder asignado aún).
- `token` es el JWT que el frontend reenvía en cada request siguiente como `Authorization: Bearer <token>`.

### Response — 401 Unauthorized

Cualquier body es válido; el frontend solo usa el status code para mostrar "Correo o contraseña incorrectos."

```json
{ "message": "Credenciales inválidas" }
```

## Cómo lo usa el frontend

- Todas las peticiones salen a rutas relativas `/api/...` (ver `frontend/src/environments/environment*.ts` → `apiUrl: '/api'`).
- En desarrollo (`ng serve`), `frontend/proxy.conf.json` reenvía `/api` → `http://localhost:8080`, así que corre el backend en el puerto **8080** sin configurar CORS para desarrollo local.
- En producción, Nginx deberá hacer ese mismo proxy de `/api` hacia el backend (pendiente en la Fase de despliegue).
- El interceptor (`core/interceptors/auth.interceptor.ts`) agrega automáticamente `Authorization: Bearer <token>` a cada request una vez logueado, y si el backend responde **401** en cualquier endpoint, cierra sesión y redirige a `/login` — o sea, cualquier endpoint protegido que quieras armar ya funciona con esto sin tocar el frontend.

## Pendiente para siguientes fases (no bloquea esta)

- Endpoint de logout en servidor (invalidar token) si se decide usar blacklist en vez de solo expiración.
- Endpoint `GET /api/auth/me` si en el futuro se necesita revalidar la sesión contra el servidor en vez de confiar en lo guardado en localStorage.
- Recuperación de contraseña.
