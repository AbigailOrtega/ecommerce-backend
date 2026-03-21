package com.ecommerce.repository;

import com.ecommerce.entity.PickupAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PickupAvailabilityRepository extends JpaRepository<PickupAvailability, Long> {
    List<PickupAvailability> findByLocationIdAndActiveTrue(Long locationId);
    Optional<PickupAvailability> findByIdAndLocationId(Long id, Long locationId);
}
