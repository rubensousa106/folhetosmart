package com.folhetosmart.products;

import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.products.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    /** Máximo de promoções a devolver no ecrã de início (pesquisa vazia). */
    private static final int PROMO_LIMIT = 50;

    private final ProductRepository productRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    public Page<ProductDto> search(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            // Pesquisa vazia -> promoções da semana atual (ecrã de início útil).
            var promos = productRepository
                    .findOnPromotion(LocalDate.now(clock), PageRequest.of(0, PROMO_LIMIT))
                    .stream()
                    .map(ProductDto::from)
                    .toList();
            return new PageImpl<>(promos, PageRequest.of(0, PROMO_LIMIT), promos.size());
        }
        return productRepository.search(search.trim(), pageable).map(ProductDto::from);
    }

    /** Produtos com preço válido esta semana — leitura para a cache da app. */
    public Page<ProductDto> currentWeek(Pageable pageable) {
        return productRepository.findCurrentWeek(LocalDate.now(clock), pageable)
                .map(ProductDto::from);
    }

    public Product getEntity(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Produto não encontrado: " + id));
    }

    public ProductDto getById(UUID id) {
        return ProductDto.from(getEntity(id));
    }
}
