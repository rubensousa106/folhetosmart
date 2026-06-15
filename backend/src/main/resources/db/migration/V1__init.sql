-- =====================================================================
-- FolhetoSmart — schema inicial (Flyway, fonte de verdade)
-- Idempotente para coexistir com db/init.sql (mount de primeiro arranque).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    brand VARCHAR(100),
    category VARCHAR(100),
    weight_grams INTEGER,
    barcode VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_canonical_name ON products (canonical_name);
CREATE INDEX IF NOT EXISTS idx_products_brand ON products (brand);
CREATE INDEX IF NOT EXISTS idx_products_category ON products (category);
CREATE INDEX IF NOT EXISTS idx_products_display_name_trgm
    ON products USING gin (display_name gin_trgm_ops);

CREATE TABLE IF NOT EXISTS supermarkets (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,
    flyer_day VARCHAR(20),
    flyer_available BOOLEAN DEFAULT false,
    flyer_available_since TIMESTAMPTZ,
    website_url TEXT
);

CREATE TABLE IF NOT EXISTS weekly_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    supermarket_id INTEGER REFERENCES supermarkets(id),
    price DECIMAL(8,2) NOT NULL,
    original_price DECIMAL(8,2),
    is_promotion BOOLEAN DEFAULT false,
    promotion_label VARCHAR(100),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    raw_product_name VARCHAR(300),
    source_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_weekly_prices_product ON weekly_prices (product_id);
CREATE INDEX IF NOT EXISTS idx_weekly_prices_supermarket ON weekly_prices (supermarket_id);
CREATE INDEX IF NOT EXISTS idx_weekly_prices_validity ON weekly_prices (valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_weekly_prices_promotion ON weekly_prices (is_promotion) WHERE is_promotion = true;
CREATE UNIQUE INDEX IF NOT EXISTS uq_weekly_prices_week
    ON weekly_prices (product_id, supermarket_id, valid_from);

CREATE TABLE IF NOT EXISTS product_aliases (
    id SERIAL PRIMARY KEY,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    alias VARCHAR(300) NOT NULL,
    supermarket_id INTEGER REFERENCES supermarkets(id),
    ai_confidence FLOAT,
    verified_manually BOOLEAN DEFAULT false,
    UNIQUE(alias, supermarket_id)
);
CREATE INDEX IF NOT EXISTS idx_product_aliases_product ON product_aliases (product_id);

CREATE TABLE IF NOT EXISTS sync_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    triggered_by VARCHAR(20),
    status VARCHAR(20),
    supermarkets_ready INTEGER DEFAULT 0,
    supermarkets_total INTEGER DEFAULT 5,
    products_matched INTEGER DEFAULT 0,
    products_unmatched INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ DEFAULT now(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_sync_runs_status ON sync_runs (status);
CREATE INDEX IF NOT EXISTS idx_sync_runs_started ON sync_runs (started_at DESC);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    fcm_token TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS price_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    target_price DECIMAL(8,2),
    any_promotion BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_price_alerts_user ON price_alerts (user_id);
CREATE INDEX IF NOT EXISTS idx_price_alerts_active ON price_alerts (is_active) WHERE is_active = true;

-- Seed dos 5 supermercados
INSERT INTO supermarkets (name, slug, flyer_day, website_url) VALUES
    ('Lidl',        'lidl',        'thursday', 'https://www.lidl.pt/c/folheto/'),
    ('Continente',  'continente',  'thursday', 'https://www.continente.pt/folhetos/'),
    ('Pingo Doce',  'pingo-doce',  'thursday', 'https://www.pingodoce.pt/folhetos/'),
    ('Intermarché', 'intermarche', 'thursday', 'https://www.intermarche.pt/folhetos'),
    ('Aldi',        'aldi',        'thursday', 'https://www.aldi.pt/ofertas.html')
ON CONFLICT (slug) DO NOTHING;
