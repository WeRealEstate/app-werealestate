# We Real Estate

**Español** · [English](#english)

CRM/ERP para una empresa de venta de lotes en Oaxaca, México. Reúne en un solo sistema la captación de prospectos, la cotización, el inventario de lotes, las ventas y su cobranza, las comisiones de asesores y las finanzas de la empresa. Además incluye páginas públicas (cotizador, planos de lotes, formularios y material de venta) para asesores y clientes.

## Módulos

| Módulo | Qué hace |
|---|---|
| Leads | Prospectos por asesor, numeración consecutiva, seguimientos, etiquetas, importación desde Excel |
| Pipeline | Tablero de seguimiento con columnas personalizables |
| Cotizador | Cotizaciones con distintos esquemas de pago (MSI, enganche, aportaciones, contado, promociones) |
| Lotes | Inventario por desarrollo, estados y bitácora de movimientos |
| Plano | Mapa interactivo de lotes con apartado de uno o varios lotes |
| Ventas | Ventas con varios lotes, copropietarios, recibos, contrato y tabla de amortización |
| Score | Ranking de asesores por número de ventas |
| Clientes | Ficha de cliente, compras y pagos atrasados |
| Finanzas | Valor vendido, calendario de ingresos y comisiones |
| Gastos | Egresos, gastos recurrentes y nómina semanal |
| Calendario | Eventos y tareas |
| Asesores externos | Contratos, jerarquía de equipos y comercializadoras |
| Comunidades We | Árbol de comunidades de asesores |
| We Forms | Formularios públicos de registro de venta |
| Material | Material de venta (copys, flyers, brochures, videos, galerías) |

Acceso por roles (Asesor, Administración, Equipo interno y Admin) y por módulo.

## Stack

- **Backend:** Spring Boot 4, Java 25, Spring Data JPA, Flyway, PostgreSQL, JWT, PDFBox, Web Push (VAPID) y correo por SMTP.
- **Frontend:** Angular 21 (componentes standalone y signals), Tailwind CSS 4, Lucide, jsPDF, ExcelJS / SheetJS.
- **PWA** con notificaciones push.

## Estructura

```
backend/    API REST (Spring Boot)
  src/main/java/com/werealestate/backend/
    controller/  service/  repository/  model/  dto/  exception/  security/
  src/main/resources/db/migration/   migraciones Flyway (solo aditivas)
frontend/   Aplicación web (Angular)
  src/app/
    core/       guards, interceptores, servicios, modelos, utilidades
    features/   pantallas por módulo
    shared/     componentes reutilizables
```

## Correrlo en local

### Requisitos

- Java 25 y Maven
- Node.js y npm
- PostgreSQL en `localhost:5432`

### Base de datos

Crea una base `werealestate` con el usuario `werealestate` y contraseña `werealestate` (valores por defecto de desarrollo). Flyway crea las tablas al arrancar el backend.

### Backend (puerto 8080)

```bash
cd backend
mvn spring-boot:run
```

Variables de entorno opcionales:

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a la base de datos |
| `JWT_SECRET`, `JWT_EXPIRATION_MINUTES` | Firma y duración de la sesión. **En producción define siempre un `JWT_SECRET` propio, largo y aleatorio** |
| `TURNSTILE_ENABLED`, `TURNSTILE_SECRET_KEY` | Captcha de Cloudflare en el login |
| `LEAD_DIAS_FRIO`, `LEAD_DIAS_SIN_CONTACTAR` | Reglas de leads fríos |
| `LOTE_DIAS_APARTADO_EXPIRA` | Días para que un apartado se libere solo |
| `UPLOADS_DIR`, `UPLOADS_PRIVADOS_DIR` | Carpetas de archivos públicos y privados |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Cuenta de Gmail y contraseña de aplicación para los correos (sin ellos no se envía nada); opcionales `MAIL_HOST`, `MAIL_PORT`, `MAIL_STARTTLS` |

### Frontend (puerto 4200)

```bash
cd frontend
npm install
npm start
```

El servidor de desarrollo redirige `/api` y `/uploads` al backend en el puerto 8080.

### Compilar para producción

```bash
cd frontend && npm run build
cd backend && mvn package
```

## Notas

- Las migraciones de base de datos son **solo aditivas**: nunca se editan las ya aplicadas.
- El código, la interfaz y los comentarios están en español.

---

<a id="english"></a>

# We Real Estate (English)

CRM/ERP for a land-lot sales company in Oaxaca, Mexico. A single system for lead capture, quoting, lot inventory, sales and collections, advisor commissions and company finances. It also ships public pages (quote calculator, lot maps, forms and sales material) for advisors and customers.

## Modules

| Module | What it does |
|---|---|
| Leads | Per-advisor prospects, sequential numbering, follow-ups, tags, Excel import |
| Pipeline | Follow-up board with customizable columns |
| Quote calculator | Quotes with several payment schemes (installments, down payment, contributions, cash, promotions) |
| Lots | Inventory per development, statuses and movement log |
| Map | Interactive lot map, with single or multi-lot reservation |
| Sales | Multi-lot sales, co-owners, receipts, contract and amortization table |
| Score | Advisor ranking by number of sales |
| Customers | Customer record, purchases and overdue payments |
| Finance | Sold value, income calendar and commissions |
| Expenses | Expenses, recurring costs and weekly payroll |
| Calendar | Events and tasks |
| External advisors | Contracts, team hierarchy and marketing groups |
| We Communities | Advisor community tree |
| We Forms | Public sales-registration forms |
| Material | Sales material (copy, flyers, brochures, videos, galleries) |

Role-based access (Advisor, Administration, Internal team and Admin) plus per-module permissions.

## Stack

- **Backend:** Spring Boot 4, Java 25, Spring Data JPA, Flyway, PostgreSQL, JWT, PDFBox, Web Push (VAPID) and SMTP mail.
- **Frontend:** Angular 21 (standalone components and signals), Tailwind CSS 4, Lucide, jsPDF, ExcelJS / SheetJS.
- **PWA** with push notifications.

## Structure

```
backend/    REST API (Spring Boot)
  src/main/java/com/werealestate/backend/
    controller/  service/  repository/  model/  dto/  exception/  security/
  src/main/resources/db/migration/   Flyway migrations (additive only)
frontend/   Web app (Angular)
  src/app/
    core/       guards, interceptors, services, models, utilities
    features/   screens per module
    shared/     reusable components
```

## Running locally

### Requirements

- Java 25 and Maven
- Node.js and npm
- PostgreSQL on `localhost:5432`

### Database

Create a `werealestate` database with user `werealestate` and password `werealestate` (development defaults). Flyway creates the tables when the backend starts.

### Backend (port 8080)

```bash
cd backend
mvn spring-boot:run
```

Optional environment variables:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Database connection |
| `JWT_SECRET`, `JWT_EXPIRATION_MINUTES` | Session signing and lifetime. **In production always set your own long, random `JWT_SECRET`** |
| `TURNSTILE_ENABLED`, `TURNSTILE_SECRET_KEY` | Cloudflare captcha on login |
| `LEAD_DIAS_FRIO`, `LEAD_DIAS_SIN_CONTACTAR` | Cold-lead rules |
| `LOTE_DIAS_APARTADO_EXPIRA` | Days before a reservation releases itself |
| `UPLOADS_DIR`, `UPLOADS_PRIVADOS_DIR` | Public and private file folders |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail account and app password for emails (nothing is sent without them); optional `MAIL_HOST`, `MAIL_PORT`, `MAIL_STARTTLS` |

### Frontend (port 4200)

```bash
cd frontend
npm install
npm start
```

The dev server proxies `/api` and `/uploads` to the backend on port 8080.

### Production build

```bash
cd frontend && npm run build
cd backend && mvn package
```

## Notes

- Database migrations are **additive only**: applied ones are never edited.
- Code, UI and comments are in Spanish.
