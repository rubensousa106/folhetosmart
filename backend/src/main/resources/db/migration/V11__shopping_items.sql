-- =====================================================================
-- Lista de compras por utilizador — sincroniza web <-> app Android.
-- Cada linha é uma OFERTA: produto + supermercado + preço + quantidade.
-- A chave de unicidade (user, produto, supermercado) espelha o productId do
-- Room na app ("<produto>::<supermercado>"). Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS shopping_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    produto       VARCHAR(200) NOT NULL,
    supermercado  VARCHAR(100) NOT NULL,
    preco         DECIMAL(8,2) NOT NULL,
    quantity      INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unicidade por oferta de cada utilizador (nome igual ao @UniqueConstraint da
-- entidade ShoppingItem, para a validação do Hibernate e o Flyway coincidirem).
CREATE UNIQUE INDEX IF NOT EXISTS uq_shopping_items_user_produto_super
    ON shopping_items (user_id, produto, supermercado);

CREATE INDEX IF NOT EXISTS idx_shopping_items_user ON shopping_items (user_id);
