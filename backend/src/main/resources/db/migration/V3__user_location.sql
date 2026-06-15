-- =====================================================================
-- Fix 1/2 — Localização do utilizador (distrito + cidade)
-- Usada pelo scraper do Aldi (folhetos regionais). Escolhida manualmente
-- no registo — nunca por GPS.
-- =====================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS district VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS city     VARCHAR(100);
