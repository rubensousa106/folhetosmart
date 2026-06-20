-- Folheto mais recente por supermercado (modelo simples "scraper -> JSON ->
-- backend -> app"). O produtor (drive_producer.py) faz POST do JSON
-- {supermercado, produtos:[...]} e a app lê-o em GET /api/v1/products/latest.
CREATE TABLE IF NOT EXISTS latest_products (
    supermarket     VARCHAR(50)  PRIMARY KEY,   -- chave normalizada (lowercase)
    payload         TEXT         NOT NULL,       -- o JSON tal e qual, para a app
    products_count  INT          NOT NULL DEFAULT 0,
    source_flyer    VARCHAR(300),                -- nome do folheto já analisado (flag "analisar 1×")
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
