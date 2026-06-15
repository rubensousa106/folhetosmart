-- =====================================================================
-- FolhetoSmart — dados de demonstração (desenvolvimento)
-- Produtos e preços realistas para a semana corrente, identificados com
-- source_url = 'seed:demo'. Idempotente; reaplicável.
-- Aplicar:  docker exec -i folhetosmart-postgres psql -U folheto -d folhetosmart < db/seed_demo.sql
-- Limpar:   DELETE FROM weekly_prices WHERE source_url = 'seed:demo';
-- =====================================================================

INSERT INTO products (canonical_name, display_name, brand, category, weight_grams) VALUES
    ('doritos_150g',                    'Doritos 150g',                       'Doritos',     'snacks',     150),
    ('leite_meio_gordo_mimosa_1l',      'Leite Meio-Gordo Mimosa 1L',         'Mimosa',      'laticínios', 1000),
    ('azeite_virgem_extra_gallo_750ml', 'Azeite Virgem Extra Gallo 750ml',    'Gallo',       'mercearia',  750),
    ('cafe_delta_lote_chavena_250g',    'Café Delta Lote Chávena 250g',       'Delta',       'mercearia',  250),
    ('arroz_agulha_cigala_1kg',         'Arroz Agulha Cigala 1kg',            'Cigala',      'mercearia',  1000),
    ('esparguete_milaneza_500g',        'Esparguete Milaneza 500g',           'Milaneza',    'mercearia',  500),
    ('atum_bom_petisco_120g',           'Atum Bom Petisco 120g',              'Bom Petisco', 'conservas',  120),
    ('iogurte_grego_danone_4x120g',     'Iogurte Grego Danone 4x120g',        'Danone',      'laticínios', 480),
    ('papel_higienico_renova_12rolos',  'Papel Higiénico Renova 12 Rolos',    'Renova',      'higiene',    NULL),
    ('cerveja_super_bock_6x33cl',       'Cerveja Super Bock 6x33cl',          'Super Bock',  'bebidas',    NULL),
    ('agua_luso_6x1_5l',                'Água Luso 6x1,5L',                   'Luso',        'bebidas',    NULL),
    ('detergente_skip_30_capsulas',     'Detergente Skip 30 Cápsulas',        'Skip',        'limpeza',    NULL)
ON CONFLICT (canonical_name) DO NOTHING;

-- Preços da semana corrente (CURRENT_DATE .. +6 dias)
INSERT INTO weekly_prices (
    product_id, supermarket_id, price, original_price, is_promotion,
    promotion_label, valid_from, valid_until, raw_product_name, source_url
)
SELECT p.id, s.id, v.price, v.original_price, v.is_promotion,
       v.promotion_label, CURRENT_DATE, CURRENT_DATE + 6, v.raw_name, 'seed:demo'
FROM (VALUES
    -- canonical                      | slug          | preço | orig  | promo | label   | nome no folheto
    ('doritos_150g',                   'lidl',         1.39,  1.99,  true,  '-30%',  'Doritos Tortilla Chips 150g'),
    ('doritos_150g',                   'continente',   1.49,  NULL,  false, NULL,    'Doritos Tortilla Spicy 150g'),
    ('doritos_150g',                   'pingo-doce',   1.45,  1.79,  true,  '-19%',  'Doritos Chilli 150g'),
    ('doritos_150g',                   'intermarche',  1.59,  NULL,  false, NULL,    'Doritos 150 g'),
    ('doritos_150g',                   'aldi',         1.42,  NULL,  false, NULL,    'Tortilla Chips Doritos 150g'),

    ('leite_meio_gordo_mimosa_1l',     'continente',   0.95,  1.09,  true,  '-13%',  'Leite Mimosa Meio Gordo 1L'),
    ('leite_meio_gordo_mimosa_1l',     'pingo-doce',   0.93,  NULL,  false, NULL,    'Leite UHT Meio Gordo Mimosa 1lt'),
    ('leite_meio_gordo_mimosa_1l',     'intermarche',  0.99,  NULL,  false, NULL,    'Mimosa Leite M/Gordo 1L'),

    ('azeite_virgem_extra_gallo_750ml','lidl',         6.99,  NULL,  false, NULL,    'Azeite V. Extra Gallo 750ml'),
    ('azeite_virgem_extra_gallo_750ml','continente',   7.49,  NULL,  false, NULL,    'Azeite Virgem Extra Gallo 0.75L'),
    ('azeite_virgem_extra_gallo_750ml','pingo-doce',   6.89,  8.99,  true,  '-23%',  'Gallo Azeite Virgem Extra 750 ml'),
    ('azeite_virgem_extra_gallo_750ml','intermarche',  7.29,  NULL,  false, NULL,    'Azeite Gallo VE 750ml'),
    ('azeite_virgem_extra_gallo_750ml','aldi',         6.95,  NULL,  false, NULL,    'Azeite Virgem Extra Gallo 750'),

    ('cafe_delta_lote_chavena_250g',   'continente',   3.79,  4.29,  true,  '-12%',  'Café Delta Chávena 250g'),
    ('cafe_delta_lote_chavena_250g',   'pingo-doce',   3.85,  NULL,  false, NULL,    'Delta Café Lote Chávena 250 g'),
    ('cafe_delta_lote_chavena_250g',   'lidl',         3.69,  NULL,  false, NULL,    'Café Moído Delta 250g'),

    ('arroz_agulha_cigala_1kg',        'lidl',         1.15,  NULL,  false, NULL,    'Arroz Agulha Cigala 1kg'),
    ('arroz_agulha_cigala_1kg',        'continente',   1.19,  NULL,  false, NULL,    'Arroz Cigala Agulha 1 kg'),
    ('arroz_agulha_cigala_1kg',        'intermarche',  1.09,  1.29,  true,  '-15%',  'Cigala Arroz Agulha 1Kg'),

    ('esparguete_milaneza_500g',       'pingo-doce',   0.89,  NULL,  false, NULL,    'Esparguete Milaneza 500 g'),
    ('esparguete_milaneza_500g',       'continente',   0.95,  NULL,  false, NULL,    'Massa Esparguete Milaneza 500g'),
    ('esparguete_milaneza_500g',       'aldi',         0.85,  0.99,  true,  '-14%',  'Esparguete Milaneza 500g'),

    ('atum_bom_petisco_120g',          'continente',   1.29,  1.55,  true,  '-17%',  'Atum Posta Azeite Bom Petisco 120g'),
    ('atum_bom_petisco_120g',          'pingo-doce',   1.35,  NULL,  false, NULL,    'Bom Petisco Atum Azeite 120 g'),
    ('atum_bom_petisco_120g',          'lidl',         1.31,  NULL,  false, NULL,    'Atum em Azeite Bom Petisco 120g'),

    ('iogurte_grego_danone_4x120g',    'continente',   2.19,  NULL,  false, NULL,    'Iogurte Grego Natural Danone 4x120g'),
    ('iogurte_grego_danone_4x120g',    'pingo-doce',   2.09,  2.49,  true,  '-16%',  'Danone Iogurte Grego 4x120 g'),
    ('iogurte_grego_danone_4x120g',    'intermarche',  2.25,  NULL,  false, NULL,    'Iogurte Grego Danone Pack 4'),

    ('papel_higienico_renova_12rolos', 'continente',   4.99,  5.99,  true,  '-17%',  'Papel Higiénico Renova Compacto 12R'),
    ('papel_higienico_renova_12rolos', 'lidl',         4.79,  NULL,  false, NULL,    'P. Higiénico Renova 12 Rolos'),
    ('papel_higienico_renova_12rolos', 'aldi',         4.85,  NULL,  false, NULL,    'Renova Papel Hig. 12 rolos'),

    ('cerveja_super_bock_6x33cl',      'continente',   3.49,  NULL,  false, NULL,    'Cerveja Super Bock 6x33cl'),
    ('cerveja_super_bock_6x33cl',      'pingo-doce',   3.39,  3.99,  true,  '-15%',  'Super Bock Original 6x33 cl'),
    ('cerveja_super_bock_6x33cl',      'intermarche',  3.55,  NULL,  false, NULL,    'Super Bock Pack 6x0,33L'),
    ('cerveja_super_bock_6x33cl',      'lidl',         3.45,  NULL,  false, NULL,    'Cerveja Super Bock 6x0.33L'),

    ('agua_luso_6x1_5l',               'continente',   2.39,  NULL,  false, NULL,    'Água Luso 6x1,5L'),
    ('agua_luso_6x1_5l',               'intermarche',  2.29,  2.59,  true,  '-12%',  'Luso Água s/ Gás 6x1.5L'),
    ('agua_luso_6x1_5l',               'aldi',         2.35,  NULL,  false, NULL,    'Água Mineral Luso 6x1,5 L'),

    ('detergente_skip_30_capsulas',    'continente',   8.99,  11.99, true,  '-25%',  'Detergente Skip Ultimate 30 Cáps.'),
    ('detergente_skip_30_capsulas',    'pingo-doce',   9.29,  NULL,  false, NULL,    'Skip Cápsulas 30 Doses'),
    ('detergente_skip_30_capsulas',    'lidl',         8.79,  NULL,  false, NULL,    'Skip Detergente 30 cápsulas')
) AS v(canonical, slug, price, original_price, is_promotion, promotion_label, raw_name)
JOIN products p ON p.canonical_name = v.canonical
JOIN supermarkets s ON s.slug = v.slug
ON CONFLICT (product_id, supermarket_id, valid_from) DO UPDATE SET
    price = EXCLUDED.price,
    original_price = EXCLUDED.original_price,
    is_promotion = EXCLUDED.is_promotion,
    promotion_label = EXCLUDED.promotion_label;
