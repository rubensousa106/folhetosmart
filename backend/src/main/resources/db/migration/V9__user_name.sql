-- =====================================================================
-- Nome do utilizador (editável no perfil, dentro do menu Definições).
-- =====================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(100);
