-- =====================================================================
-- Estado de sincronização por supermercado (ecrã Sincronizar — 4 estados)
-- =====================================================================

ALTER TABLE supermarkets ADD COLUMN IF NOT EXISTS
    sync_status VARCHAR(20) DEFAULT 'pending';   -- pending | running | success | error
ALTER TABLE supermarkets ADD COLUMN IF NOT EXISTS
    products_imported INTEGER DEFAULT 0;
ALTER TABLE supermarkets ADD COLUMN IF NOT EXISTS
    synced_at TIMESTAMPTZ;
ALTER TABLE supermarkets ADD COLUMN IF NOT EXISTS
    error_message TEXT;
