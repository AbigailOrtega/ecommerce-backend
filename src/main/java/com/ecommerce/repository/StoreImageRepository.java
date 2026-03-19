package com.ecommerce.repository;

import com.ecommerce.entity.StoreImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreImageRepository extends JpaRepository<StoreImage, Long> {
    List<StoreImage> findAllByActiveTrueOrderByDisplayOrderAsc();
}
