package com.ecommerce.repository;

import com.ecommerce.entity.ProductSize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSizeRepository extends JpaRepository<ProductSize, Long> {

    /** Atomically takes stock; returns 0 (and changes nothing) if there is not enough. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductSize s SET s.stock = s.stock - :qty WHERE s.id = :id AND s.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductSize s SET s.stock = s.stock + :qty WHERE s.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
