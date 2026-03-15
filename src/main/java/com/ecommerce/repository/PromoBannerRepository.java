package com.ecommerce.repository;

import com.ecommerce.entity.PromoBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoBannerRepository extends JpaRepository<PromoBanner, Long> {
    List<PromoBanner> findAllByActiveTrueOrderByCreatedAtAsc();
}
