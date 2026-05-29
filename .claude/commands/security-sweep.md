---
description: Marketplace security-requirements sweep — cross-check against CAKE.com publishing guidelines
---
Run a security review on the current diff (or the full addon if no diff), then
cross-check against the CAKE.com security requirements in
addon-expenses-rest-api/MARKETPLACE_OCS/12-publishing-and-guidelines.md.

Check each item and output a pass/fail table with file:line evidence:

| Requirement | Status | Evidence |
|---|---|---|
| JWT verification: RS256, iss=clockify, type=addon, sub=key, exp, workspace match | | |
| Installation token never in frontend JS/HTML | | |
| Installation token never in logs | | |
| Webhook + lifecycle signature verified | | |
| TLS enforced, HSTS header present | | |
| Least-privilege scopes (no TASK_READ, no extra scopes) | | |
| No CAKE.com credentials stored in the add-on | | |
| No hardcoded Clockify API hosts | | |
| No float/double for money/distance | | |
| Dependency vulnerability scan result | | |
| CSP header present | | |
| no-store / no-cache on auth-gated responses | | |

State PASS, FAIL, or SKIPPED (with reason) for each row. $ARGUMENTS
