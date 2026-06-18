package com.folhetosmart.data.models

data class SupermarketResponse(
    val supermercado: String,
    val produtos: List<Product>
)

data class Product(
    val produto: String,
    val preco: Double
)
