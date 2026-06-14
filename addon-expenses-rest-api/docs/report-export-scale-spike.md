# Report and Export Scale Spike

Date: 2026-06-15

## Current Behavior

- CSV exports stream through `MileageConversionCsvExporter`.
- Printable `/iframe/report` scans live Clockify expenses, merges converted mileage rows, caps display at 1000 rows, and warns when either the Clockify scan or report row cap is hit.
- The report is a browser print-to-PDF surface, not a stored PDF generator.

## Decision

Do not build async report/export jobs yet.

## Reasoning

- Existing CSV streaming avoids buffering the full export in memory.
- Existing report caps and truncation notices prevent accidental giant HTML documents.
- A real async export feature requires durable job storage, expiry/cleanup, download auth, and user-visible job state. That is a product feature, not a quick hardening fix.

## Revisit When

- A real workspace needs more than 1000 printable rows in a single reimbursement report.
- Support sees repeated report truncation complaints.
- CSV exports become slow enough that request streaming times out.

## Future Shape

If revisited, add a dedicated export job table with workspace/user ownership, requested date range, scope, status, generated artifact metadata, expiry time, and admin/member authorization checks. Keep generated artifacts out of logs and never expose installation tokens.
