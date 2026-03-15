package com.ecommerce.repository;

import com.ecommerce.entity.PickupLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PickupLocationRepository extends JpaRepository<PickupLocation, Long> {
    List<PickupLocation> findAllByActiveTrueOrderByNameAsc();
}
