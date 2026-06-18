package com.folhetosmart.data.models

data class Product(
    val produto: String,
    val preco: Double
)

data class SupermarketResponse(
    val supermercado: String,
    val produtos: List<Product>
)
