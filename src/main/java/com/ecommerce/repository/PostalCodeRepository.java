package com.ecommerce.repository;

import com.ecommerce.entity.PostalCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostalCodeRepository extends JpaRepository<PostalCode, Long> {
    List<PostalCode> findByCodeOrderBySettlementAsc(String code);
}
