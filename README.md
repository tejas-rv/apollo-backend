# Apollo Elevators Backend

Spring Boot 4.1 and Java 21 REST API for the Apollo Elevators platform.
The service provides JWT authentication, customer/lift/AMC management,
engineer service reports, PDF documents, enquiries, quotations, work orders,
and email/WhatsApp notifications.

## Stack

- Java 21 and Gradle
- Spring Boot Web, Validation, Security, Data JPA, Actuator, and Mail
- PostgreSQL with Flyway migrations
- JWT access and refresh tokens
- Thymeleaf and OpenHTMLToPDF for documents
- Springdoc OpenAPI

## Local setup

1. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

2. Set the required environment variables:

   ```text
   APP_MASTER_KEY=<base64 32-byte key>
   DB_URL=jdbc:postgresql://localhost:5432/apollo-elevator-development
   DB_USERNAME=apollo
   DB_PASSWORD=<database password>
   ```

   Generate a master key with `openssl rand -base64 32`. The master key is
   used to encrypt secrets stored in the `system_secret` table and must never
   be committed.

3. Run the service:

   ```bash
   .\gradlew.bat bootRun
   ```

   Flyway applies migrations automatically. On the first start, the
   application creates the encrypted JWT configuration and a default admin
   user, logging the generated password once.

### Test customers (development only)

Start with `SEED_TEST_CUSTOMERS=true` to create ten sample customers
(`TEST-001`..`TEST-010`) covering overdue/today/upcoming/cancelled/completed
visits, multiple lifts, expired/pending/cancelled AMCs, unlinked legacy
engineers and customers without lifts or AMCs. Existing codes are skipped;
delete them and restart to regenerate with fresh dates.

The API is available at `http://localhost:9071` by default. OpenAPI documentation is
available at `/swagger-ui/index.html`; health is available at
`/actuator/health`.

## Optional integrations

Set these values only when the corresponding integration is enabled:

```text
EMAIL_ENABLED=true
EMAIL_FROM=noreply@example.com
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=<smtp user>
MAIL_PASSWORD=<smtp password>

WHATSAPP_ENABLED=true
WHATSAPP_PHONE_NUMBER_ID=<Meta phone number id>
WHATSAPP_ACCESS_TOKEN=<Meta access token>
WHATSAPP_API_VERSION=v20.0

GOOGLE_DRIVE_ENABLED=true
GOOGLE_DRIVE_APPLICATION_NAME=apollo-elevators
GOOGLE_DRIVE_OAUTH_CLIENT_PATH=C:\path\outside\the\repository\apollo-elevator-oauth-client.json
GOOGLE_DRIVE_TOKEN_DIRECTORY=C:\path\outside\the\repository\google-drive-tokens
GOOGLE_DRIVE_FOLDER_ID=<Apollo Elevator Data folder ID>
```

Email, WhatsApp, and Google Drive are disabled unless explicitly enabled.
Database connectivity uses the standard Spring datasource properties and does
not silently switch to a different database when the configured database is
unavailable.

Google Drive integration uses OAuth 2.0 user authorization (installed-app
flow), not a service account. On first startup, a browser window opens for
the Google account to grant Drive access; the resulting refresh token is
persisted under `GOOGLE_DRIVE_TOKEN_DIRECTORY` so later restarts do not
require signing in again. Uploads are stored under
`<customer database ID>_<customer name>/Contracts`, `Bills`, or
`Service History`. Contract filenames receive a `_YYYY` suffix; bill and
service-history filenames receive a `_ddMMMyyyy` suffix. The integration does
not change file permissions or make uploaded files public.

To generate and upload a customer's documents without supplying any files,
call `POST /api/google-drive/customer-documents/{customerId}`. The endpoint
generates the AMC contract PDF, GST bill PDF, and one service-history PDF per
recorded service visit using the existing document-generation services, then
uploads each to the customer's `Contracts`, `Bills`, or `Service History`
subfolder. `contract` and `bill` are `null` in the response when the customer
has no corresponding data (e.g. no active AMC contract); `serviceHistory` is a
list and may be empty.

## API areas

| Area | Main paths |
| --- | --- |
| Authentication | `/api/auth/**` |
| Admin | `/api/admin/**` |
| Engineer | `/api/engineer/**` |
| Serviceability (ADMIN) | `/api/serviceability/**` |
| API documentation | `/swagger-ui/**`, `/v3/api-docs/**` |
| Health | `/actuator/health` |

Admin and engineer endpoints require a JWT in the
`Authorization: Bearer <token>` header. Admin users can refresh the encrypted
runtime security configuration with
`POST /api/admin/security/config/refresh`.

### Serviceability and engineer allocation

The admin Serviceability screen is available at `/app/serviceability`. Flyway
migration `V23` creates configurable Bengaluru zones, PIN-code mappings and
audit history, effective-dated engineer-zone assignments, engineer capacity
settings, and service-assignment history. The migration seeds only the four
zone definitions; it deliberately does not seed unverified PIN-code data.

Administrators can manage zones and mappings, configure primary and backup
engineers, review allocation suggestions, confirm automatic assignments,
reassign visits, and create/resequence a daily plan. Automatic allocation
requires an active, supervisor-verified PIN-code mapping and an active zone
engineer. Ambiguous or unverified records remain unassigned with an explicit
reason. Assignment priority is CRITICAL when overdue, HIGH when due today,
MEDIUM within three days, NORMAL within seven days, and LOW thereafter.
Primary engineers are preferred; available backups are used when primary
capacity is reached. The allocator balances job counts within configured daily
capacity; no road-distance or route optimisation is claimed.

PIN-code bulk import accepts CSV, XLS, and XLSX files (maximum 5 MB) with
`pincode`, `locality`, and `source` columns; `district`, `state`,
`postalDivision`, and `zoneId` are optional. Imported rows are always marked
UNVERIFIED and require supervisor review before automatic allocation. Use
official India Post references for postal data; postal divisions must not be
treated as Apollo zones.

Priority thresholds and default engineer capacity can be configured with:

```text
APOLLO_SERVICEABILITY_MEDIUM_DUE_WITHIN_DAYS=3
APOLLO_SERVICEABILITY_NORMAL_DUE_WITHIN_DAYS=7
APOLLO_SERVICEABILITY_DEFAULT_DAILY_CAPACITY=5
```

The main API paths are `GET /api/serviceability/dashboard`,
`GET /api/serviceability/zones`, `GET|POST /api/serviceability/pincodes`,
`POST /api/serviceability/pincodes/import`,
`POST /api/serviceability/engineer-zone-assignments`,
`POST /api/serviceability/assign` (preview with `confirm=false`, then submit
again with `confirm=true`), `GET /api/serviceability/daily-plan`, and
`PUT /api/serviceability/assignments/{id}/reassign`.

### Customer data import modes

`POST /api/admin/customers/data/import?mode=MERGE|OVERRIDE` (default `MERGE`).
Exports include a `visitId` column in `service_history`; edited visits are
matched by it, so always edit a fresh export.

- `MERGE` adds and updates rows; nothing is deleted.
- `OVERRIDE` first saves a full export to Google Drive as
  `<importedFileName>_<yyyyMMdd_HHmmss>_<username>.xlsx` (IST; aborted if the
  backup fails, rejected with 409 when Drive is disabled), then replaces the
  representatives and the service visits of each AMC contract in the file.
  Customers, lifts and AMC contracts are never deleted, and COMPLETED visits are
  kept when the file was exported with `includeCompleted=false`.

## Project layout

```text
src/main/java/com/apollo/elevator/
  authorization/          Authentication and users
  customer/               Customers, lifts, AMC contracts
  documents/              PDF generation
  engineer/               Engineer dashboard and service reports
  enquiry/                Contact enquiries
  notification/           Email and WhatsApp delivery
  quotation/              Quotations
  securityconfiguration/  JWT and encrypted runtime secrets
  workorder/               Work-order workflow
  common/                 API errors and shared configuration
src/main/resources/
  db/migration/            Versioned Flyway migrations
  templates/pdf/           Thymeleaf document templates
```

## Verification

```bash
.\gradlew.bat test
.\gradlew.bat build
```

Keep database credentials, API tokens, generated PDFs, and local build output
out of source control. See `BackendRequirements.md` for the detailed
functional endpoint specification.
