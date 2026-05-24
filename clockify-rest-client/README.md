# Clockify REST Client

Java + Spring Boot friendly Clockify REST API client facade.

This module is intentionally built from documented/provenanced endpoints only. The client contains core transport/config/auth/pagination/binary/raw-client infrastructure plus typed clients for users, workspaces, clients, projects, tasks, tags, time entries, reports, files, user groups, holidays, invoices, expenses, custom fields, approvals, webhooks, scheduling, time off (policies, requests, balances), policies, shared reports, and add-on settings. Audit logs are not exposed as a supported add-on client surface unless an allowed official source documents the route and add-on auth behavior.

Safety defaults:

- no token values in `toString()` output
- bounded response bodies
- explicit pagination only
- backend and reports base URLs kept separate
- binary responses handled separately from JSON
- raw fallback requires explicit bounded requests
- reports use the reports host
- retries are opt-in via `ClockifyRetryPolicy`
- custom headers and user-agent are configurable

To use the client, you can use the builder or rely on Spring Boot Auto-configuration.

### Java Builder usage
```java
ClockifyClient client = ClockifyClient.builder()
        .apiKey(System.getenv("CLOCKIFY_API_KEY")) // or .addonToken(installationToken)
        .workspaceId(System.getenv("CLOCKIFY_WORKSPACE_ID"))
        .backendBaseUrl("http://localhost:8081") // String or URI overload
        .reportsBaseUrl("http://localhost:8082") // String or URI overload
        .retryPolicy(ClockifyRetryPolicy.defaults())
        .build();

User me = client.users().current();
```

## Spring Boot Auto-Configuration

This library includes optional auto-configuration for Spring Boot applications. The configuration registers a `ClockifyClient` bean only when `clockify.apiKey` or `clockify.addonToken` is explicitly configured. Add-on applications that use per-workspace installation tokens should normally build clients through their installation-token factory instead of configuring a global static credential.

### Configuration Properties

You can configure the client properties in `application.properties` or `application.yml` under the `clockify` prefix:

```properties
clockify.apiKey=${CLOCKIFY_API_KEY}
# OR clockify.addonToken=${CLOCKIFY_ADDON_TOKEN} (addonToken takes precedence if both are set)

clockify.workspaceId=your-workspace-id
clockify.backendBaseUrl=https://api.clockify.me/api
clockify.reportsBaseUrl=https://reports.api.clockify.me
clockify.requestTimeout=30s
clockify.maxResponseBytes=10485760
clockify.followRedirects=false
```

### Data Region & Subdomain Base URL Routing Guidelines

Configure `clockify.backendBaseUrl` (Regular APIs) and `clockify.reportsBaseUrl` (Reports APIs) depending on the subdomain and data region settings of your workspace:

* **Global** (Workspaces with or without subdomains):
  * **Regular Base URL**: `https://api.clockify.me/api`
  * **Reports Base URL**: `https://reports.api.clockify.me`
* **Regional (Non-subdomain)**:
  * **Regular Base URL**: `https://euc1.clockify.me/api` (or other regional domain)
  * **Reports Base URL**: `https://use2.clockify.me/report` (or other regional report domain)
* **Regional (Subdomain)**:
  * **Regular Base URL**: `https://euc1.clockify.me/api` (or other regional domain)
  * **Reports Base URL**: `https://yoursubdomainname.clockify.me/report`
* **Developer Environment**:
  * **Regular Base URL**: `https://developer.clockify.me/api`
  * **Reports Base URL**: `https://developer.clockify.me/report`

---


## Spring MVC controller facade

`com.cake.clockify.client.spring.ClockifyRestController` is an optional thin Spring MVC facade over typed `ClockifyClient` methods. It is disabled by default and is registered only when `clockify.rest-controller.enabled=true` and a `ClockifyClient` bean exists. Do not expose this facade from public add-on iframes unless you add your own application authorization layer.

### Error Handling

The rest controller includes a localized exception handler (`handleClockifyApiException`) that catches `ClockifyApiException` and passes the response status and sanitized error body transparently back to callers, ensuring consistent error contracts without leaking secrets.

Current controller coverage is tracked in `docs/rest-controller-openapi-coverage.md`: 191 supported official facade operations are exposed when the facade is explicitly enabled. Two add-on settings routes are separate add-on/prose endpoints. Full typed Java model/client coverage remains a separate provenance-driven task.


## Add-on Authentication & Security

### User Token Exchange

When building a Clockify Add-on, you can exchange the installation token for a specific user token using:

```java
// Using the Java Client
String userToken = client.users().exchangeUserToken(userId);
```

The Spring MVC facade intentionally does not expose this raw-token exchange endpoint. Keep token exchange server-side and return only the minimum derived data your add-on UI needs.

### Invoice Export

Clockify requires `userLocale` for invoice PDF export. The no-arg convenience uses `en-US`; production add-ons with locale preferences should pass an explicit locale tag:

```java
ClockifyBinaryResponse pdf = client.invoices().exportInvoice(workspaceId, invoiceId, "en-US");
```

### JWT Signature and Claim Verification

To secure inbound webhook events and lifecycle events (e.g., verifying `clockify-signature` headers), the library includes a native `ClockifyTokenVerifier` which is automatically registered as a Spring bean.

#### Standard Token Claims & Verification
Use `verifyToken` to check token validity and extract a strongly typed `ClockifyClaims` record:

```java
import com.cake.clockify.client.auth.ClockifyTokenVerifier;
import com.cake.clockify.client.auth.ClockifyClaims;

// Injected or instantiated
ClockifyTokenVerifier verifier = new ClockifyTokenVerifier();

try {
    // Verifies RS256 signature, expiry, iss=clockify, type=addon, and sub=addonManifestKey
    ClockifyClaims claims = verifier.verifyToken(token, "your-addon-manifest-key");
    
    String workspaceId = claims.workspaceId();
    String userId = claims.userId();
    
    // Securely process request context...
} catch (SecurityException e) {
    // Invalid signature, expired, or mismatching claims
}
```

#### Dynamic Client Provisioning
You can automatically configure and initialize a `ClockifyClient` routed to the correct regional domains based directly on the verified claims of the incoming request:

```java
ClockifyClaims claims = verifier.verifyToken(token, "your-addon-manifest-key");
ClockifyClient client = ClockifyClient.fromClaims(claims, token);
```

Pass a verified user token from `auth_token` or a server-held installation token here; webhook signatures are verification-only and cannot authenticate Clockify API calls.

#### Webhook Signature Validation
Use `verifyWebhook` to authorize incoming event payloads, automatically preventing cross-workspace security bypasses:

```java
try {
    verifier.verifyWebhook(signatureHeader, "your-addon-manifest-key", payloadWorkspaceId);
    // Signature is valid and matches the workspace ID in the payload. Securely process event...
} catch (SecurityException e) {
    // Rejected signature or mismatching workspace context
}
```
