package com.folhetosmart.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupermarketRepository extends JpaRepository<Supermarket, Integer> {

    List<Supermarket> findAllByOrderByIdAsc();

    Optional<Supermarket> findBySlug(String slug);

    long countByFlyerAvailableTrue();
}
