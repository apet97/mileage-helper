UPDATE addon_webhook_jobs
SET status = 'COMPLETED'
WHERE status = 'CONVERTED';
