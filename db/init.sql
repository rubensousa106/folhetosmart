-- =====================================================================
-- FolhetoSmart — Schema PostgreSQL 16
-- Comparador semanal de preços de supermercados portugueses
-- =====================================================================

-- gen_random_uuid() vem do módulo pgcrypto (nativo no PG 13+)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- Para pesquisa fuzzy de nomes de produtos (fallback ao matching da IA)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- Produtos canónicos (normalizados pela IA)
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    brand VARCHAR(100),
    category VARCHAR(100),
    weight_grams INTEGER,
    barcode VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX uq_products_canonical_name ON products (canonical_name);
CREATE INDEX idx_products_brand ON products (brand);
CREATE INDEX idx_products_category ON products (category);
-- Índice trigram para pesquisa "search=doritos"
CREATE INDEX idx_products_display_name_trgm ON products USING gin (display_name gin_trgm_ops);

-- ---------------------------------------------------------------------
-- Supermercados
-- ---------------------------------------------------------------------
CREATE TABLE supermarkets (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,
    flyer_day VARCHAR(20),                 -- "thursday"
    flyer_available BOOLEAN DEFAULT false,
    flyer_available_since TIMESTAMPTZ,
    website_url TEXT,
    -- Estado de sincronização (ecrã Sincronizar — 4 estados)
    sync_status VARCHAR(20) DEFAULT 'pending',  -- pending | running | success | error
    products_imported INTEGER DEFAULT 0,
    synced_at TIMESTAMPTZ,
    error_message TEXT,
    sync_source VARCHAR(20)                      -- site | drive | upload | null
);

-- ---------------------------------------------------------------------
-- Preços semanais (uma linha por produto/supermercado/semana)
-- ---------------------------------------------------------------------
CREATE TABLE weekly_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    supermarket_id INTEGER REFERENCES supermarkets(id),
    price DECIMAL(8,2) NOT NULL,
    original_price DECIMAL(8,2),
    is_promotion BOOLEAN DEFAULT false,
    promotion_label VARCHAR(100),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    raw_product_name VARCHAR(300),         -- nome original no folheto
    source_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_weekly_prices_product ON weekly_prices (product_id);
CREATE INDEX idx_weekly_prices_supermarket ON weekly_prices (supermarket_id);
CREATE INDEX idx_weekly_prices_validity ON weekly_prices (valid_from, valid_until);
CREATE INDEX idx_weekly_prices_promotion ON weekly_prices (is_promotion) WHERE is_promotion = true;
-- Evita duplicados do mesmo produto/super/semana
CREATE UNIQUE INDEX uq_weekly_prices_week
    ON weekly_prices (product_id, supermarket_id, valid_from);

-- ---------------------------------------------------------------------
-- Aliases de produto (nomes alternativos por supermercado, gerados pela IA)
-- ---------------------------------------------------------------------
CREATE TABLE product_aliases (
    id SERIAL PRIMARY KEY,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    alias VARCHAR(300) NOT NULL,
    supermarket_id INTEGER REFERENCES supermarkets(id),
    ai_confidence FLOAT,                   -- score de confiança do Claude
    verified_manually BOOLEAN DEFAULT false,
    UNIQUE(alias, supermarket_id)
);

CREATE INDEX idx_product_aliases_product ON product_aliases (product_id);

-- ---------------------------------------------------------------------
-- Execuções de sincronização (automatizador)
-- ---------------------------------------------------------------------
CREATE TABLE sync_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    triggered_by VARCHAR(20),              -- "user" | "cron"
    status VARCHAR(20),                    -- "pending" | "running" | "done" | "error"
    supermarkets_ready INTEGER DEFAULT 0,
    supermarkets_total INTEGER DEFAULT 5,
    products_matched INTEGER DEFAULT 0,
    products_unmatched INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_sync_runs_status ON sync_runs (status);
CREATE INDEX idx_sync_runs_started ON sync_runs (started_at DESC);

-- ---------------------------------------------------------------------
-- Utilizadores
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',       -- 'USER' | 'ADMIN'
    fcm_token TEXT,
    consent_given_at TIMESTAMPTZ,          -- RGPD: quando aceitou os termos
    consent_version VARCHAR(10),           -- RGPD: versão dos termos aceites
    deletion_requested_at TIMESTAMPTZ,     -- RGPD: pedido de eliminação (auditoria)
    district VARCHAR(50),                  -- localização p/ folheto Aldi (manual)
    city VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- ---------------------------------------------------------------------
-- RGPD — auditoria de ações de privacidade
-- user_id fica NULL após hard delete (o registo de auditoria sobrevive)
-- ---------------------------------------------------------------------
CREATE TABLE consent_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(50),   -- "consent_given" | "consent_withdrawn" | "data_exported" | "account_deleted"
    version     VARCHAR(10),
    ip_hash     VARCHAR(64),   -- SHA-256 do IP, nunca o IP em claro
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_consent_log_user ON consent_log (user_id);
CREATE INDEX idx_consent_log_created ON consent_log (created_at DESC);

-- ---------------------------------------------------------------------
-- Alertas de preço
-- ---------------------------------------------------------------------
CREATE TABLE price_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    target_price DECIMAL(8,2),
    any_promotion BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_price_alerts_user ON price_alerts (user_id);
CREATE INDEX idx_price_alerts_active ON price_alerts (is_active) WHERE is_active = true;

-- =====================================================================
-- Seed — os 5 supermercados suportados
-- =====================================================================
INSERT INTO supermarkets (name, slug, flyer_day, website_url) VALUES
    ('Lidl',        'lidl',        'thursday', 'https://www.lidl.pt/c/folheto/'),
    ('Continente',  'continente',  'thursday', 'https://www.continente.pt/folhetos/'),
    ('Pingo Doce',  'pingo-doce',  'thursday', 'https://www.pingodoce.pt/folhetos/'),
    ('Intermarché', 'intermarche', 'thursday', 'https://www.intermarche.pt/folhetos'),
    ('Aldi',        'aldi',        'thursday', 'https://www.aldi.pt/ofertas.html')
ON CONFLICT (slug) DO NOTHING;
