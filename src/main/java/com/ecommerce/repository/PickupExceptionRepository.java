package com.ecommerce.repository;

import com.ecommerce.entity.PickupException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PickupExceptionRepository extends JpaRepository<PickupException, Long> {

    boolean existsByAvailabilityIdAndDate(Long availabilityId, LocalDate date);

    List<PickupException> findByAvailabilityId(Long availabilityId);

    Optional<PickupException> findByIdAndAvailabilityId(Long id, Long availabilityId);
}
