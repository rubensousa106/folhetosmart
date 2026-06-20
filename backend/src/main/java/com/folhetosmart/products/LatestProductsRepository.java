package com.folhetosmart.products;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso ao folheto mais recente por supermercado (chave = slug minúsculo). */
public interface LatestProductsRepository extends JpaRepository<LatestProducts, String> {
}
