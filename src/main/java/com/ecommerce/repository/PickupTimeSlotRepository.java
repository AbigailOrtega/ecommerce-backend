package com.ecommerce.repository;

import com.ecommerce.entity.PickupTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PickupTimeSlotRepository extends JpaRepository<PickupTimeSlot, Long> {
    List<PickupTimeSlot> findByLocationIdAndActiveTrue(Long locationId);
}
