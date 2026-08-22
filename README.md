# Apollo Elevators — Backend

Java 21 + Spring Boot 4.1 + Gradle. JWT auth, with the JWT secret and other
sensitive config stored in the database rather than in `application.yml`.

## How the DB-stored secrets work (read this first)

You asked for JWT secret/username/password-type sensitive fields to come from
the database instead of config files. Here's the actual design, and *why*
it's built this way:

- **`system_secret` table** holds the JWT signing secret, token expiry, and
  issuer — encrypted with AES-256-GCM before being written.
- **One master key** (`APP_MASTER_KEY`, an environment variable) encrypts and
  decrypts those DB values. This key **cannot** live in the database itself —
  if it did, anyone with DB access could decrypt everything, defeating the
  point. It lives only in your hosting platform's environment variables
  (Render/Railway "Environment" tab), never in source control.
- **Database connection credentials** (`DB_USERNAME`, `DB_PASSWORD`)
  unavoidably stay as environment variables too — you can't fetch the
  password for a database from inside that same database before you've
  connected to it. This is standard practice, not a shortcut.
- **User passwords** (admin/engineer/customer logins) are in the `app_user`
  table as BCrypt hashes — this part matches what you asked for directly.

On first startup, `SecurityInitializer` auto-generates a random JWT secret,
encrypts it, and stores it in `system_secret` — you never hand-craft it. It
also creates a default `admin` user with a random password printed to the
console **once**. Copy it immediately; build a "change password" endpoint
before going further.

## Local setup

0. **Add the Gradle wrapper** (not included in this download — generate it
   once with a local Gradle install, so future clones don't need Gradle
   pre-installed):
   ```bash
   gradle wrapper --gradle-version 8.11
   ```
1. **Generate a master key:**
   ```bash
   openssl rand -base64 32
   ```
2. **Start Postgres locally:**
   ```bash
   docker compose up -d
   ```
3. **Set environment variables** (or use a `.env` loader / your IDE's run config):
   ```bash
   export APP_MASTER_KEY="<paste the key from step 1>"
   export DB_URL="jdbc:postgresql://localhost:5432/apollo"
   export DB_USERNAME="apollo"
   export DB_PASSWORD="apollo"
   ```
4. **Run:**
   ```bash
   ./gradlew bootRun
   ```
   Watch the console for the generated admin password on first run.

5. **Log in:**
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"<printed password>"}'
   ```
   Returns an access token plus a refresh token. Use the access token as
   `Authorization: Bearer <token>` on subsequent requests to `/api/admin/**`
   or `/api/engineer/**`.

## Auth endpoints available now

- `POST /api/auth/login` — returns access + refresh tokens
- `POST /api/auth/refresh` — rotates the refresh token and returns a fresh pair
- `GET /api/auth/me` — returns the current authenticated user
- `POST /api/auth/reset-password` — ADMIN-only password reset

## WhatsApp notification config

The backend now includes an admin-only WhatsApp send endpoint backed by the
Meta WhatsApp Cloud API:

- `POST /api/admin/notifications/whatsapp`
- `POST /api/admin/notifications/email`
- `POST /api/admin/notifications/html-to-pdf`

`/api/admin/notifications/email` supports optional attachments by passing
`attachments` in request payload, where each item has:
- `fileName`
- `contentType`
- `base64Content`

`/api/admin/notifications/html-to-pdf` accepts HTML content, generates a PDF,
and sends it via email and/or WhatsApp document:
- `htmlContent` (required)
- `pdfFileName` (optional, defaults to generated-document.pdf)
- `email` / `emailSubject` / `emailMessage` (optional)
- `whatsappPhoneNumber` / `whatsappCaption` (optional)
- at least one target (`email` or `whatsappPhoneNumber`) is required

Set these environment variables before using it:

```bash
export WHATSAPP_ENABLED=true
export WHATSAPP_PHONE_NUMBER_ID="<meta phone number id>"
export WHATSAPP_ACCESS_TOKEN="<meta permanent access token>"
export WHATSAPP_API_VERSION="v20.0"
export EMAIL_ENABLED=true
export EMAIL_FROM="noreply@yourdomain.com"
export MAIL_HOST="<smtp host>"
export MAIL_PORT="587"
export MAIL_USERNAME="<smtp username>"
export MAIL_PASSWORD="<smtp password>"
```

You can send from Outlook to Gmail (or Gmail to Outlook) by switching SMTP
host/credentials to the sender account's provider and keeping:
- `MAIL_USERNAME` = actual sender mailbox login
- `EMAIL_FROM` = same sender mailbox address

Examples:
- Gmail SMTP: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`
- Outlook SMTP: `MAIL_HOST=smtp.office365.com`, `MAIL_PORT=587`

## Rotating the JWT secret later

Update the row in `system_secret` (encrypt the new value with the same
`AesEncryptionUtil` logic — a small admin CLI or endpoint for this is a
natural next addition), then call:
```
POST /api/admin/security/config/refresh   (ADMIN token required)
```
No restart needed — existing tokens signed with the old secret will stop
validating, so this effectively logs everyone out. Plan rotations accordingly.

## Deploying free (Render/Railway)

- Add `APP_MASTER_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` as environment
  variables in the platform's dashboard — not in any file you commit.
- Add `WHATSAPP_ENABLED`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`,
  and optionally `WHATSAPP_API_VERSION` when enabling WhatsApp delivery.
- Add `EMAIL_ENABLED`, `EMAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
  and `MAIL_PASSWORD` when enabling email delivery.
- Point `DB_URL` at your free Postgres instance (Supabase/Neon/Render Postgres).
- Flyway runs the migrations in `db/migration/` automatically on startup.

## What's next

- `/api/admin/**` business endpoints: Elevator, AMC, Billing, Modernization,
  QC checklist, Scheduling, and reporting flows from the earlier project guide.
- `/api/engineer/**` endpoints: assigned visits, QC submission, and job status
  updates.
- Role-based row-level restrictions (e.g. an ENGINEER should only see their
  own assigned visits).
- Additional notification channels (email/SMS) on top of the new notification
  log and WhatsApp foundation.
