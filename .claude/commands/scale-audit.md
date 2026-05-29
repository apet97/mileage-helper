---
description: Audit webhook latency, DB pool, and observability for scale — read-only, produces a fix plan
---
Invoke the `clockify-scale-hardening` skill in READ-ONLY audit mode. Report the
current state of each gap with file:line evidence:

- G1: sync webhook processing (look in WebhookController, handler.handle() call path)
- G2: Hikari pool config (check application.yaml for spring.datasource.hikari.*)
- G3: metrics/observability (check actuator config, look for Micrometer usage)
- G4: dependency vulnerability scanning (check pom.xml for dependency-check plugin)

Produce a prioritized fix list. Do not edit any files — propose a plan via
ExitPlanMode. $ARGUMENTS
