# Tech Stack

## Backend

- Java 21 source/target through Maven `release 21`.
- Spring Boot 3.x.
- Maven.
- Official `com.cake.clockify:addon-sdk`.
- Local `clockify-rest-client` module for Clockify API access.
- Jackson for JSON.
- Jakarta Bean Validation.
- Spring Web MVC.
- Spring Data JPA.
- Hibernate.
- PostgreSQL.
- Flyway or Liquibase for migrations.

## Frontend

MVP:
- Server-served HTML.
- Vanilla JavaScript or TypeScript.
- Clockify/CAKE UI Kit if available and easy to use.
- No heavy SPA required.

Optional later:
- React/Vite if UI grows.

## Testing

- JUnit 5.
- AssertJ.
- Mockito.
- Testcontainers PostgreSQL.
- WireMock or OkHttp MockWebServer for Clockify API.
- JSON Schema validator for manifest validation.
- Playwright only if iframe UI e2e tests are needed.

## DevOps

- Dockerfile.
- Docker Compose for local PostgreSQL.
- CI is not configured in this extracted repo yet. Local release checks are Maven tests, Docker build, and manifest probe.
- Local tunnel for Clockify dev testing:
  - ngrok
  - cloudflared
  - localtunnel

## Runtime configuration

Environment variables:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
ADDON_BASE_URL
ADDON_KEY
ADDON_NAME
ADDON_DESCRIPTION
ADDON_CRYPTO_ACTIVE_KEY_ID
ADDON_CRYPTO_KEY_K1
ADDON_ENABLE_HSTS
PORT
```

Live sacrificial checks may additionally use `CLOCKIFY_API_BASE_URL`, `CLOCKIFY_API_KEY`, `CLOCKIFY_WORKSPACE_ID`, `CLOCKIFY_TEST_USER_ID`, `CLOCKIFY_TEST_PROJECT_ID`, and `CLOCKIFY_TEST_TASK_ID`. Keep values in the shell environment and never print secrets.

## Clockify add-on platform

- Manifest schema: `1.5`.
- Minimum subscription plan: `PRO`.
- Scopes:
  - `EXPENSE_READ`
  - `EXPENSE_WRITE`
  - `USER_READ`
  - `PROJECT_READ`
  - optional `WORKSPACE_READ`
- Webhooks:
  - `EXPENSE_CREATED`
  - `EXPENSE_UPDATED`
  - `EXPENSE_DELETED`
  - `EXPENSE_RESTORED`
- Lifecycle:
  - `INSTALLED`
  - `DELETED`
  - `SETTINGS_UPDATED`
  - `STATUS_CHANGED`

## Data precision

Use:
- Java `BigDecimal`
- SQL `numeric`
- String DTOs for decimal inputs

Avoid:
- `double`
- `Double`
- `float`
- `Float`

## Current package structure

```text
com.cake.clockify.addon.mileage
  MileageAddonApplication

com.cake.clockify.addon.mileage.config
  MileageManifestConfig
  MileageManifestV15

com.cake.clockify.addon.mileage.lifecycle
  MileageLifecycleHandler

com.cake.clockify.addon.mileage.webhook
  ExpenseCreatedWebhookHandler
  ExpenseUpdatedWebhookHandler
  ExpenseDeletedWebhookHandler
  ExpenseRestoredWebhookHandler

com.cake.clockify.addon.mileage.conversion
  MileageConversionService
  MileageEligibilityService
  ConversionResult

com.cake.clockify.addon.mileage.calculation
  MileageCalculator

com.cake.clockify.addon.mileage.note
  MileageNoteService

com.cake.clockify.addon.mileage.clockify
  ClockifyExpenseGateway
  ClockifyExpenseSnapshot
  ClockifyCategoryOption

com.cake.clockify.addon.mileage.settings
  MileageSettingsService
  MileageWorkspaceSettings
  MileageSettingsRepository

com.cake.clockify.addon.mileage.audit
  MileageConversion
  MileageConversionRepository

com.cake.clockify.addon.mileage.api
  MileageApiController
  MileageSettingsController
  MileageConversionController

com.cake.clockify.addon.mileage.iframe
  MileageIframeController
```
