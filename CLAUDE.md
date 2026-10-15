# We Real Estate — mapa del proyecto

CRM/ERP de una empresa de lotes en Oaxaca (zona horaria `America/Mexico_City`, idioma de código y UI: español).
Monorepo: `backend/` (Spring Boot) + `frontend/` (Angular). Este archivo existe para no tener que releer todo:
**lee solo los archivos del módulo que vas a tocar.**

## Reglas de trabajo (del usuario)
- **Nunca asumir nada; siempre preguntar**, las veces que haga falta, antes de actuar (alcance, comportamiento esperado, datos, nombres, UI, casos borde).
- **Las preguntas se hacen en vivo con la herramienta `AskUserQuestion`** (opciones seleccionables), una ronda tras otra, y el usuario las va contestando mientras trabajas. **No terminar el turno con una pregunta en texto** ni cerrar el mensaje esperando respuesta: pregunta, recibe la respuesta, y sigue en el mismo turno. Si la respuesta abre otra duda, vuelve a preguntar.
- **No hacer `git push`** salvo que lo pida en ese momento; antes de pushear: `fetch` + `pull --rebase`.
- La BD del VPS (Hostinger) tiene **datos reales**: migraciones Flyway **solo aditivas**, avisar antes de desplegar, no tocar datos.
- Bugs de coordenadas en canvas con zoom: medir el rect renderizado real (un border de 1px del contenedor se multiplica por el zoom).
- Cada cambio visible: agregar entrada al inicio de `frontend/src/app/core/changelog.ts` (la versión mostrada es la más alta de ese archivo) y mantener `frontend/package.json` igual.

## Stack y cómo correr
- Backend: Spring Boot 4.1.1, Java 25, JPA (`ddl-auto=validate`), Flyway, Postgres, JWT (jjwt 0.12), pdfbox, web-push. Puerto 8080.
- Frontend: Angular 21 (standalone, signals, lazy `loadComponent`), Tailwind 4, `@lucide/angular`, jspdf, exceljs/xlsx, motion. Puerto 4200; `proxy.conf.json` manda `/api` y `/uploads` a :8080.
- BD local: Postgres **nativo Homebrew en :5432** (no docker-compose). Credenciales por defecto en `application.properties` (`werealestate`/`werealestate`).
- Dev servers: `.claude/launch.json` (`backend` = `mvn spring-boot:run`, `frontend` = `npm start`).
- Env backend: `DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, JWT_EXPIRATION_MINUTES, TURNSTILE_ENABLED, TURNSTILE_SECRET_KEY, LEAD_DIAS_FRIO, LEAD_DIAS_SIN_CONTACTAR, LOTE_DIAS_APARTADO_EXPIRA, UPLOADS_DIR, UPLOADS_PRIVADOS_DIR`.
- Build frontend: `npm run build` (prebuild escribe `build-info.ts`; postbuild `scripts/paginas-publicas.mjs`).

## Backend: `backend/src/main/java/com/werealestate/backend/`
Capas: `controller/` → `service/` → `repository/` (Spring Data) + `model/` (entidades y enums) + `dto/` (records). Excepciones en `exception/` (`GlobalExceptionHandler` las traduce: Validation→400, Forbidden→403, NotFound→404, Conflict→409, ResponseStatus→su código con `message`).
Migraciones: `src/main/resources/db/migration/V1…V63` (la siguiente es **V65**).

### Seguridad y permisos
- `SecurityConfig`: stateless, JWT. Públicos: `/api/auth/**`, `GET /api/promociones/activas`, `POST /api/cotizaciones/publica`, `GET /api/lotes/publico`, `PUT /api/lotes/publico/*/estado`, `POST /api/lotes/publico/verificar-asesor`, `GET|POST /api/forms-publico/**`. Cadena aparte para `/uploads/**` (cacheable, público).
- `JwtAuthFilter` confía en el rol del token (no consulta BD). `CurrentUserProvider.getUsuarioActual()` sí consulta por email.
- Roles (`Role`): `ASESOR, LIDER_AREA` (en UI = "Administración"), `EQUIPO_INTERNO, ADMIN`.
- 13 módulos (`Modulo`): LEADS, PIPELINE, COTIZADOR, LOTES, PLANO, VENTAS, CLIENTES, FINANZAS, GASTOS, CALENDARIO, ASESORES_EXTERNOS, COMUNIDADES, WE_FORMS. `ModulosAcceso` define máximo y default por rol; `Usuario.modulos` (CSV, null = default del rol).
- `ModuloAccesoInterceptor`: mapa prefijo de API → módulos que lo habilitan (más específico primero). **Un endpoint nuevo de un módulo debe agregarse ahí.** APIs compartidas (desarrollos, usuarios, notificaciones, tareas, reportes) solo por rol.
- Cada servicio valida su rol con helpers tipo `exigirAdminOLider()` / `exigirAdmin(...)`. Acciones destructivas piden la contraseña de quien las hace (eliminar venta: admin y administración, con cuenta regresiva de 10 s en la UI; limpiar mapa: solo admin).
- Login: `AuthService` + `TurnstileService` (captcha Cloudflare, desactivado por defecto). PIN público: `AsesorPublicoService` + `LimitadorPinPublico` (5 fallos / 10 min por IP, usa `X-Forwarded-For`).
- Usuario de sistema `cotizador-publico@weinversiones.com` (V17) atribuye acciones sin sesión (cotizador público, apartado público, registros de venta).

### Dominio (servicios grandes — leerlos antes de tocar su área)
- **VentaService** (≈920 l.): ventas con varios lotes (`VentaLote`), aportaciones, copropietarios (hasta 4 + principal), recibos (`ReciboVenta` con líneas `PagoVenta` por `TipoAbono`: ENGANCHE, MENSUALIDAD, APORTACION, APARTADO). Recibos se **anulan**, no se borran (bitácora `ReciboVentaHistorial`). Número de venta consecutivo, manual o auto (mayor+1), con `bloquearNumeracion()` y recorrido de la racha; al eliminar, las mayores bajan 1. Contrato firmado (`registrarContrato`). `crearDesdeRegistroDeVenta` → lotes en `EN_PROCESO_DE_FIRMA`, fecha provisional, `depositoPendiente` aplicable con `aplicarDeposito`. Solo ADMIN/LIDER.
- **ComisionService** (≈730 l.): comisión 5% por defecto (máx 10%) por venta + una secundaria para copropietario. Devengos reconstruidos por completo desde los abonos en cada `sincronizar`. Regla nueva (`reglaNueva`): enganche cuenta completo, mensualidad/aportación la mitad, apartado 0; entrega el sábado siguiente al abono y solo con contrato firmado (`Totales.bloqueada`). Entregar crea un `Gasto` origen COMISION; devolver/anular entrega soportados. Las ventas viejas conservan la regla anterior (UNA_EXHIBICION / PARCIALIDADES).
- **LoteService** (≈630 l.): estados `DISPONIBLE, APARTADO, APARTADO_A_PLAZO, APARTADO_CON_DINERO, EN_PROCESO_DE_FIRMA, VENDIDO`. Los 4 últimos solo los mueve ADMIN/LIDER (con nota obligatoria). Todo cambio pasa por `cambiarEstadoConHistorial` → `MovimientoLote`. Versión pública `cambiarEstadoPublico` (sin estados restringidos, no libera `APARTADO`). `LoteAutoLiberacionScheduler` (cada 5 min) libera vencidos. Plano: polígonos en JSON (`PoligonoMapaJson`), imagen en `UPLOADS_DIR`.
- **FinanzasService**: valor vendido por desarrollo/lote y calendario de ingresos esperado vs recibido (libro con contra-asientos de recibos anulados y devoluciones de comisión); `cobroPorVenta()` calcula atrasos. Carga todo en memoria por llamada.
- **LeadService / SeguimientoService / EtiquetaService / ColumnaPersonalizadaService**: leads por asesor (admin ve todos), "fríos" tras 15 días, archivar, reasignar, importar Excel por fila, etiquetas y columnas del pipeline privadas por asesor.
- **ClienteService**: ficha con `paisResidencia` (validado contra `model/Paises` + `resources/paises.txt`; la misma lista está en `frontend/src/app/core/data/paises.ts`, editar ambas) y `estado` (si el país es México debe ser uno de los 32 de `model/EstadosMexico` + `resources/estados-mx.txt`, espejo en `core/data/estados-mx.ts`; con otro país, texto libre) y número consecutivo editable (`cambiarNumero`), validación CURP/RFC, anti-duplicado (CURP / nombre+nacimiento / teléfono → `ClienteDuplicadoException`), `resolverDesdeRegistroDeVenta` (vincula por CURP, nunca por nombre). Al arrancar migra ventas viejas a clientes.
- **WeFormService / WeFormPublicoService**: formularios de registro de venta tipo Google Forms (preguntas con `DestinoPregunta` que mapean a cliente/venta). Envío público con PIN: valida, crea cliente + venta + respuesta en una transacción; archivos ≤2 MB (JPG/PNG/WEBP/PDF por firma) en carpeta privada. `forms-publico` va por slug. Tipo de pregunta `PAIS` (selector con buscador `shared/lista-selector`, genérico para países y estados; la pregunta predefinida `ESTADO` pasa a lista de estados cuando el país elegido es México): la primera de cada formulario llena `Cliente.paisResidencia` (en cliente existente solo si está vacío).
- **GastoService / GastoRecurrenteService / TipoGastoService**: gastos con ticket privado (`UPLOADS_PRIVADOS_DIR`), recurrentes y nómina semanal (sábados) por usuario.
- **CotizacionService / PromocionService**, **AsesorExternoService** (contrato, jerarquía de equipos, ficha, PIN, acceso por proyecto samai/nanuu), **ComunidadInternosService**, **DesarrolloService** (catálogo y plano), **TareaService**, **EventoCalendarioService**, **ReporteService**, **UsuarioService** (CRUD, módulos, nómina, PIN).
- **Nombre "Registrar venta"** (antes "Precontrato"): lo que ve el usuario dice "Registrar venta"/"registro de venta". Se conservaron por estar guardados en la BD: el valor `FuenteCliente.PRECONTRATO`, los textos viejos de notas (`LoteService.NOTAS_REGISTRO_VENTA`) y la clave de aviso `PRECONTRATO_NUEVO` (`NotificacionService.TIPO_REGISTRO_VENTA_ANTERIOR`, también en `push_enviada`); no editar las migraciones V62/V64 aunque mencionen "precontrato" (cambiaría su checksum de Flyway).
- **NotificacionService**: notificaciones calculadas al vuelo (leads, tareas, eventos, lotes, ventas, respuestas We Forms); solo se guarda lo leído (`NotificacionLeida`). **PushService + PushScheduler** (cada 2 min): Web Push VAPID, llaves en tabla `push_config`, SQL vía `JdbcTemplate`.

## Frontend: `frontend/src/app/`
- `app.routes.ts`: rutas públicas (`/login`, `/samai`, `/aldea-nanuu`, `/cotizador-publico` [+`/lotes`], `/we-forms`, `/we-forms/:slug`) y `/panel/**` bajo `authGuard` + `PanelLayoutComponent`; cada ruta usa `roleGuard([...])` + `moduloGuard([...])`. `/panel/gastos` redirige a `/panel/finanzas?pestana=gastos`.
- `core/`: `guards/`, `interceptors/` (auth: agrega Bearer y hace logout en 401; carga: cuenta peticiones), `services/` (un servicio por API; `auth.service` guarda token/usuario en `localStorage` `we_auth_token`/`we_auth_user`, `tieneModulo`, `refrescarPerfil`, `panelRouteForRole`), `models/` (espejo de DTOs), `utils/` (aportaciones, plan-mensualidades, financiamiento, csv/excel, plantillas de importación, rueda de zoom, imagen-pdf), `data/proyectos-cotizador.config.ts`, `changelog.ts`.
- `features/`: `auth`, `leads`, `lotes`, `usuarios`, `asesores-externos`, `teams` (Comunidades We, lienzo con zoom), `we-forms`, `publico` (plano-publico, lotes-publico, hero, we-forms-publico, layout del cotizador), `panel/*` (admin-panel, asesor-panel, equipo-panel, pipeline, cotizador, plano, calendario, clientes, ventas [list/form/detalle], finanzas [+ingresos/valor], gastos, promociones, historial-cotizaciones, versiones).
- `shared/`: modales y selectores reutilizables (cliente-selector, aportaciones-selector, recibo-form, recibos-venta, lote-cambio-estado-modal, lote-info-modal, pin-publico, notification-bell, confirm-dialog, toast, loaders), directivas `moneda-input`, `correo-input`, `motion/*`.
- Archivos más grandes (leer por partes): `pdf-cotizacion.service.ts` (≈1840 l.), `cotizador.component.{ts,html}` (≈1630 / 1210), `plano-publico.component.ts` (≈870), `teams.component.ts` (≈770), `plano.component.ts` (≈735).
- Proyectos del cotizador/plano público: `samai` ("SAMAI Campestre"; macrolote ≥ 8000 m² usa tarifa distinta) y `nanuu` ("Aldea Nanuu"). Tipos de pago: `msi, downpayment, initial, annualities, aportaciones, cash, promocion`.
- PWA: `public/sw.js` (solo pantalla offline + push), `manifest.webmanifest`.

## Convenciones del código
- Comentarios Javadoc/TS en español explicando el "porqué"; nombres de dominio en español (`venta`, `lote`, `asesor`).
- Dinero con `BigDecimal` (backend) y `moneda-input` (frontend); fechas `LocalDate`/`LocalDateTime` sin zona.
- Listas paginadas usan `PaginaDto<T>` (`contenido`, `hayMas`) y `Specification` en el servicio.
- Entidades JPA con constructor `protected` vacío; cambios de estado mediante métodos de la entidad.

## Pendientes / riesgos conocidos
1. `JWT_SECRET` nunca seteado en el VPS (usa el default de `application.properties`) — crítico. Rotar la password de DB que se pegó en un chat.
2. `PUT /api/lotes/publico/*/estado` no exige PIN en servidor (el PIN solo gatea la UI).
3. `LimitadorPinPublico` confía en `X-Forwarded-For` (falseable si el proxy no lo sobrescribe).
4. Token válido tras desactivar un usuario (480 min por defecto).
5. Varios servicios cargan tablas completas en memoria (`FinanzasService.Datos`, `ComisionService.Totales`, `ClienteService.verificarDuplicado`, `LoteService.actualizarPrecioManzana`).
6. Código marcado "temporal": `Venta.actualizar` y su botón; conviven dos reglas de comisión (`reglaNueva`).
7. `API_CONTRACT.md` solo cubre el login y no menciona `captchaToken`; `README.md` vacío.

## Qué NO está leído a detalle (leer cuando se necesite)
DTOs, repositorios (queries nativas de numeración: `bloquearNumeracion`, `recorrer`, `bajarDesde`, `fijarNumero`), migraciones SQL, `ReporteService`, `GastoService`, `GastoRecurrenteService`, `AsesorExternoService`, `WeFormService`, `CotizacionService` y casi todos los componentes de `features/` y `shared/`.
