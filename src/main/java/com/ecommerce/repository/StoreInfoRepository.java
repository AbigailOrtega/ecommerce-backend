package com.ecommerce.repository;

import com.ecommerce.entity.StoreInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreInfoRepository extends JpaRepository<StoreInfo, Long> {
}
