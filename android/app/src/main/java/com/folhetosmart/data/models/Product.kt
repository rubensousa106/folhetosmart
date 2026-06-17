package com.folhetosmart.data.models

data class Product(
    val produto: String,
    val preco: Double
)

data class SupermarketResponse(
    val supermercado: String,
    val data_extracao: String,
    val total_produtos: Int,
    val produtos: List<Product>
)
