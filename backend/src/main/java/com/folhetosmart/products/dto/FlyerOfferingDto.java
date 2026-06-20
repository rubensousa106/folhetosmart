package com.folhetosmart.products.dto;

/**
 * Uma oferta de um produto num folheto: o produto, o preço, o supermercado e a
 * validade da semana. A app junta as ofertas com o mesmo produto e destaca a
 * mais barata (ecrã Comparar).
 */
public record FlyerOfferingDto(
        String produto,
        double preco,
        String supermercado,
        String validade
) {
}
