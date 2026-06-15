-- =====================================================================
-- Origem do folheto sincronizado (site | drive | upload)
-- Permite à app mostrar "📁 PDF disponível no Google Drive · A processar".
-- =====================================================================

ALTER TABLE supermarkets ADD COLUMN IF NOT EXISTS
    sync_source VARCHAR(20);   -- site | drive | upload | null
