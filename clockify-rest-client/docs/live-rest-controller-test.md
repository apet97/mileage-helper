# Live REST controller test

The live controller test is `ClockifyRestControllerLiveTest`.

Enable it only when live Clockify environment variables are available:

```sh
cd clockify-rest-client
set -a
source ../.env
set +a
CLOCKIFY_RUN_LIVE_REST_CONTROLLER=1 mvn -Dtest=ClockifyRestControllerLiveTest test
```

Required environment variables:

- `CLOCKIFY_API_KEY`
- `CLOCKIFY_WORKSPACE_ID`
- `CLOCKIFY_RUN_LIVE_REST_CONTROLLER=1`

What it verifies:

- Reflects every Spring MVC mapping on `ClockifyRestController`.
- Expects exactly 193 mappings, matching `/Users/15x/Downloads/clean1`.
- Invokes every controller mapping against the configured live workspace.
- Uses real env configuration without printing secrets.
- Uses synthetic IDs and minimal bodies for most mutating/id-specific routes to avoid broad live data mutation while still exercising controller-to-transport wiring.
- Accepts upstream Clockify HTTP errors because many routes are feature-gated, require real entity IDs, or intentionally receive minimal invalid bodies.
- Fails on local controller wiring errors, missing environment variables, mapping-count drift, or sanitized response bodies that leak env values.

Latest local run in this session:

- `mvn test -q` passed.
- `CLOCKIFY_RUN_LIVE_REST_CONTROLLER=1 mvn -q -Dtest=ClockifyRestControllerLiveTest test` passed after sourcing `../.env`.
