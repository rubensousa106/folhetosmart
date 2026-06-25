package com.folhetosmart.ui

/**
 * Distrito → região do folheto do Aldi (as mesmas 7 regiões do scraper). Usado
 * para a app mostrar só o "Aldi" da zona do utilizador. As chaves coincidem com
 * [DISTRITOS_PT]; as regiões com os nomes no feed ("Aldi Norte", "Aldi Centro", …).
 */
val REGIAO_POR_DISTRITO: Map<String, String> = mapOf(
    "Viana do Castelo" to "Norte", "Braga" to "Norte", "Porto" to "Norte",
    "Vila Real" to "Norte", "Bragança" to "Norte",
    "Aveiro" to "Centro", "Viseu" to "Centro", "Guarda" to "Centro",
    "Coimbra" to "Centro", "Leiria" to "Centro", "Castelo Branco" to "Centro",
    "Lisboa" to "Lisboa", "Santarém" to "Lisboa", "Setúbal" to "Lisboa",
    "Portalegre" to "Sul", "Évora" to "Sul", "Beja" to "Sul",
    "Faro" to "Algarve",
    "R. A. Açores" to "Açores",
    "R. A. Madeira" to "Madeira",
)

/** Região do distrito do utilizador, ou null se não definido/desconhecido. */
fun regiaoDoDistrito(distrito: String?): String? =
    distrito?.let { REGIAO_POR_DISTRITO[it] }
