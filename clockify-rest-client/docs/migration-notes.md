# Migration notes for existing add-ons

Existing Java add-ons contain their own Clockify HTTP code. Do not migrate them automatically; use this client incrementally.

## Observed local patterns

- `stop@estimate` uses Spring `RestClient`, separate backend/reports clients, `X-Addon-Token`, path templates, Retry-After handling, and URL normalization.
- `BreakCompliance` uses a shared Spring `RestClient`, read-only detailed reports, and defensive parsing for `timeentries` lowercase.
- `actions/clockify-http-actions` uses Spring `RestClient` with Apache HttpClient pooling for outbound calls and Clockify URL normalization.
- `goclmcp` provides broad endpoint behavior references and raw fallback safety posture, but it is background evidence only unless the current user explicitly widens the source boundary.

## Suggested migration steps

1. Add `clockify-rest-client` as a dependency.
2. Create a `ClockifyClient` bean using an API key or an add-on token provider.
3. Pass `backendUrl` and `reportsUrl` from verified JWT claims when running inside an add-on.
4. Replace read-only calls first: current user, workspace read, project/client/tag/task lists, reports.
5. Replace writes only after matching existing tests and checking endpoint provenance.
6. Keep add-on lifecycle, webhook verification, DB persistence, UI, and billing code outside this module.

## Example

```java
ClockifyClient client = ClockifyClient.builder()
    .authProvider(new AddonTokenAuthProvider(addonToken))
    .backendBaseUrl(URI.create(backendUrlFromClaims))
    .reportsBaseUrl(URI.create(reportsUrlFromClaims))
    .workspaceId(workspaceId)
    .retryPolicy(ClockifyRetryPolicy.defaults())
    .build();
```
