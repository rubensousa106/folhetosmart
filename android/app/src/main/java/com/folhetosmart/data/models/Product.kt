package com.folhetosmart.data.models

/** Uma oferta de um produto num folheto (GET /api/v1/products/all). */
data class FlyerOfferingDto(
    val produto: String,          // nome canónico (normalizado) — usado para agrupar
    val preco: Double,
    val supermercado: String,
    val validade: String? = null,
    val original: String? = null, // nome tal como aparece no folheto da loja
    val marca: String? = null     // marca nacional (ex.: Mimosa) ou vazio
)
