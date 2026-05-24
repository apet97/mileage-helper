-- Encrypt workspace settings with the same TokenCodec envelope used for installation tokens.
-- Existing plaintext rows remain readable because value_key_id is null until the next write.

ALTER TABLE addon_workspace_settings
    ADD COLUMN IF NOT EXISTS value_key_id VARCHAR(64);
