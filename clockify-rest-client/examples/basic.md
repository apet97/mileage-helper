# Basic usage

```java
ClockifyClient client = ClockifyClient.builder()
    .apiKey(System.getenv("CLOCKIFY_API_KEY"))
    .workspaceId(System.getenv("CLOCKIFY_WORKSPACE_ID"))
    .retryPolicy(ClockifyRetryPolicy.defaults())
    .build();

JsonNode me = client.users().getLoggedUser();
JsonNode workspace = client.workspaces().getWorkspaceOfUser(client.config().workspaceId());
JsonNode projects = client.projects().getProjects(client.config().workspaceId(), ClockifyPageRequest.firstPage(50));
```

## Reports

Reports use the reports host, not the main API host.

```java
ObjectMapper mapper = new ObjectMapper();
ObjectNode body = mapper.createObjectNode();
body.put("dateRangeStart", "2026-01-01T00:00:00Z");
body.put("dateRangeEnd", "2026-01-02T00:00:00Z");
body.set("summaryFilter", mapper.createObjectNode().put("groups", "PROJECT"));
JsonNode report = client.reports().summary(workspaceId, body);
```

## Raw fallback

```java
ClockifyResponse<String> response = client.raw().send(
    ClockifyRequest.builder("GET", "/v1/workspaces/" + workspaceId + "/projects")
        .query("page", 1)
        .query("page-size", 50)
        .build());
```

## Upload image

```java
JsonNode uploaded = client.files().uploadImage("avatar.png", "image/png", bytes);
```

## Error handling

```java
try {
    client.projects().getProject(workspaceId, projectId);
} catch (ClockifyNotFoundException e) {
    log.info("Clockify object not found: {} {} status={}", e.method(), e.path(), e.statusCode());
} catch (ClockifyRateLimitException e) {
    log.warn("Clockify rate limited; retry-after={}", e.retryAfter());
}
```
