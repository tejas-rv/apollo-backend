# Apollo Elevator Backend Requirements

This document captures the functional and technical requirements inferred from the code in this repository. It is intended as a product/engineering specification for the Apollo Elevator backend service.

## 1. Product overview

The project is a Spring Boot Java 21 backend for an elevator maintenance and AMC (Annual Maintenance Contract) management platform. It supports:

- authentication and role-based access control
- admin management of customers, lifts, and AMC contracts
- engineer access to customer information and service reporting
- generation of AMC contract and bill PDFs
- email and WhatsApp notifications
- secure runtime configuration using encrypted DB-stored secrets

## 2. Business actors and roles

### 2.1 ADMIN
- Full administrative access to all `/api/admin/**` endpoints.
- Can create engineer accounts, manage customers, update AMC service history, generate documents, and send notifications.
- Can rotate security configuration values and refresh runtime config without app restart.

### 2.2 ENGINEER
- Access to `/api/engineer/**` endpoints.
- Can view a restricted customer list and customer details.
- Can fetch the default service checklist.
- Can submit service reports, view their own reports, and download PDFs.

### 2.3 CUSTOMER
- Role exists in the model and JWT claims but is not currently implemented as a separate business module.
- The code recognizes `CUSTOMER` as a valid role, possibly for future customer-facing login use cases.

## 3. Functional requirements

### 3.1 Authentication and authorization

The system must provide stateless JWT-based authentication with the following behavior:

- `POST /api/auth/login`
  - accepts username and password
  - validates credentials using Spring Security
  - returns access token, refresh token, username, role, and token expiry

- `POST /api/auth/refresh`
  - accepts a refresh token
  - rotates the token and returns a new access-token pair

- `GET /api/auth/me`
  - returns the currently authenticated user profile

- `POST /api/auth/reset-password`
  - ADMIN-only operation
  - invalidates active refresh tokens before resetting password

- `POST /api/admin/users/engineers`
  - ADMIN-only
  - creates a new ENGINEER user
  - hashes password using BCrypt before saving

Authorization rules:

- public endpoints: `/api/auth/login`, `/api/auth/refresh`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`
- authenticated users: `/api/auth/**`
- admin-only: `/api/admin/**`
- engineer or admin: `/api/engineer/**`
- all other requests: authenticated

Security requirement:

- JWT access tokens include subject = username and role claim
- JWT secret, issuer, expiration values and refresh expiry are loaded from encrypted DB values and cached in memory
- CSRF is disabled because the API is stateless and token-based

### 3.2 Customer and asset management

The admin module must manage customer records and nested lift/AMC information.

Requirements include:

- create customer with nested lifts and client representatives
- get a single customer by ID
- get paginated all-customer listing
- search customer by code, name, mobile, email, city, or state
- update customer details while preserving the original customer code
- delete customer if no dependent records block deletion
- update AMC service history and automatically recalculate totals

Customer record fields include:

- customer code, name, mobile, email, address, city, state, pincode
- nested LIFT records
- nested CLIENT REPRESENTATIVE records

Lift record fields include technical details such as:

- lift type, drive type, floors, capacity, brand, model, installation year, serial number
- door type, machine details, electrical characteristics, UPS, rope/pulley details
- battery/system attributes
- AMC contract list for the lift

AMC contract record fields include:

- contract number, status, start/end date, contract type
- AMC amount, payment frequency, next payment date, next service date
- total services, completed services
- service history list

### 3.3 Engineer service workflow

The engineer domain implements a maintenance reporting workflow.

Requirements include:

- `GET /api/engineer/dashboard`
  - returns counters for services today, services this month, total submitted reports
  - returns upcoming services in the next 30 days
  - returns recent reports for the engineer

- `GET /api/engineer/customers`
  - returns a paginated list of customers in read-only form
  - excludes financial AMC amount data for restricted engineer views

- `GET /api/engineer/customers/{customerId}`
  - returns a single customer in restricted-access view

- `GET /api/engineer/service-reports/checklist-template`
  - returns the default checklist questions used for service visits

- `GET /api/engineer/service-reports`
  - returns paginated service reports for the authenticated engineer

- `GET /api/engineer/service-reports/{reportId}`
  - fetches a single report

- `POST /api/engineer/service-reports`
  - accepts a service report payload with customer, AMC contract, visit date, overall notes, and checklist answers
  - persists the report
  - generates a PDF of the report
  - emails the PDF to all configured admin recipients
  - marks report status as `SUBMITTED` and then `PDF_SENT` when delivery succeeds

- `GET /api/engineer/service-reports/{reportId}/pdf`
  - generates and downloads the PDF for a report

Checklist behavior:

- default checklist contains yes/no and descriptive questions
- the API expects that all checklist entries are present when a report is submitted
- report PDF generation is done using the server-side Thymeleaf/PDF template pipeline

### 3.4 Document generation

The system must generate printable documents from the database and from reviewed bill payloads.

#### AMC contract PDF

- `GET /api/admin/documents/customers/{customerId}` with default or specific document type
- generates customer document PDF
- default document type is `AMC_CONTRACT`
- uses a PDF template and customer/lift/AMC data loaded from the database

#### Bill generation flow

The repository implements a two-step bill workflow:

- `GET /api/admin/documents/customers/{customerId}/bill-preview?documentType=...`
  - fetches customer + AMC data
  - returns a pre-filled `BillRequest` JSON for review

- `POST /api/admin/documents/bills/generate?documentType=...`
  - accepts the reviewed bill request body
  - generates a PDF for either GST or non-GST bill

- `POST /api/admin/documents/bills/send-email?documentType=...&to=...`
  - generates the bill PDF and emails it to the provided recipient

This supports:

- `GST_BILL`
- `WITHOUT_GST_BILL`

### 3.5 Messaging and notifications

The application must support sending communication through both email and WhatsApp. Requirements include:

- `POST /api/admin/notifications/email`
  - sends a plain text email

- `POST /api/admin/notifications/email/contract`
  - generates a contract PDF and emails it as an attachment

- `POST /api/admin/notifications/whatsapp`
  - sends a plain text WhatsApp message

- `POST /api/admin/notifications/whatsapp/contract`
  - generates a contract PDF and sends it as a WhatsApp document

- `POST /api/admin/notifications/html-to-pdf`
  - accepts HTML content
  - generates a PDF
  - optionally sends it via email and/or WhatsApp
  - ensures at least one delivery target exists

Notification logging:

- every notification is persisted in `notification_log`
- status may be `PENDING`, `SENT`, or `FAILED`
- provider message IDs and failure reasons are stored when available

### 3.6 Security configuration and startup behavior

The application must keep sensitive values out of source-controlled config files where possible.

Requirements:

- app startup ensures these values exist in `system_secret`:
  - `jwt.secret`
  - `jwt.expiration-ms`
  - `jwt.refresh-expiration-ms`
  - `jwt.issuer`
- secret values are encrypted with AES-256-GCM before saving
- a master key is required to decrypt these values
- if the database secrets are rotated, the system supports runtime refresh via:
  - `POST /api/admin/security/config/refresh`

Startup defaults:

- if no admin user exists, the app creates an `admin` account with a random password generated once and logged to the console
- the generated admin password must be changed immediately after first login

## 4. Non-functional requirements

### 4.1 Platform and stack

- Java 21
- Spring Boot 4.1.x
- Gradle build system
- PostgreSQL database
- Flyway for DB migration management
- JPA/Hibernate for persistence
- Spring Security for authentication/authorization
- OpenAPI/Swagger for API documentation

### 4.2 Security requirements

- passwords must be stored as BCrypt hashes
- JWT signing secret must be kept secret and rotated without app restart
- admin endpoints require valid ADMIN role
- engineer endpoints require ADMIN or ENGINEER role
- error responses must use JSON API error format with consistent structure

### 4.3 Observability

- application logs are used throughout authentication, customer management, engineering workflows, and notification sending
- actuator health endpoint is exposed for operational checks

### 4.4 Extensibility

- modules are organized by domain: authorization, customer, engineer, documents, notification, securityconfiguration
- notification stack supports additional message channels and future integration work
- document generation is template-driven and configurable through the Thymeleaf/PDF service layer

## 5. Operational requirements

### 5.1 Environment and deployment settings

The service depends on the following runtime configuration concepts:

- database connection URL, username, and password
- `APP_MASTER_KEY` for decrypting DB secret values
- WhatsApp API configuration (`WHATSAPP_ENABLED`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, etc.)
- email configuration (`EMAIL_ENABLED`, `EMAIL_FROM`, SMTP host/port/user/password)

### 5.2 Expected integrations

- PostgreSQL is the primary persistence store
- SMTP provider for email delivery
- Meta WhatsApp Cloud API for WhatsApp delivery
- PDF generation via OpenHTML/Thymeleaf rendering pipeline

## 6. Current implementation status

Based on the code, the following are implemented or partially implemented:

- authentication, JWT issuance, and refresh token rotation
- admin user creation for engineers
- customer CRUD, search, and nested lift/AMC management
- engineer dashboards, service reporting, and PDF generation
- admin document generation for AMC contracts and bills
- notification sending via email and WhatsApp with logging
- security config loading from DB-encrypted secrets

The following areas are explicitly noted as future work in the code comments:

- role-based row-level restrictions for engineer/customer access
- additional notification channels beyond email and WhatsApp
- fuller user-management flows for admin/customer accounts
- more complete lifecycle and approval workflows around service contracts and maintenance tasks

## 7. Summary of required endpoints

Authentication:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `POST /api/auth/reset-password`

Admin user management:

- `POST /api/admin/users/engineers`

Customer management:

- `POST /api/admin/customers/create`
- `GET /api/admin/customers/getCustomerUsingId/{id}`
- `GET /api/admin/customers/getAllCustomers`
- `GET /api/admin/customers/search`
- `PUT /api/admin/customers/updateCustomerUsingId/{id}`
- `DELETE /api/admin/customers/deleteCustomerUsingId/{id}`
- `PUT /api/admin/customers/amc-contracts/{amcContractId}/service-history`

Engineer portal:

- `GET /api/engineer/dashboard`
- `GET /api/engineer/customers`
- `GET /api/engineer/customers/{customerId}`
- `GET /api/engineer/service-reports/checklist-template`
- `GET /api/engineer/service-reports`
- `GET /api/engineer/service-reports/{reportId}`
- `POST /api/engineer/service-reports`
- `GET /api/engineer/service-reports/{reportId}/pdf`

Documents:

- `GET /api/admin/documents/customers/{customerId}`
- `GET /api/admin/documents/customers/{customerId}/bill-preview`
- `POST /api/admin/documents/bills/generate`
- `POST /api/admin/documents/bills/send-email`

Notifications:

- `POST /api/admin/notifications/email`
- `POST /api/admin/notifications/email/contract`
- `POST /api/admin/notifications/whatsapp`
- `POST /api/admin/notifications/whatsapp/contract`
- `POST /api/admin/notifications/html-to-pdf`

Security admin:

- `POST /api/admin/security/config/refresh`

## 8. Acceptance criteria for a complete MVP

A complete MVP for this backend should provide:

- secure login and role-based access control
- persistent customer + lift + AMC management
- engineer service reporting workflow with PDF generation and admin delivery
- document generation and email/WhatsApp delivery
- encrypted secret storage and runtime security refresh
- OAS/Swagger documentation and health endpoint

This repository already implements the majority of those capabilities in code, with some areas intentionally left as future enhancements.
