package com.ecommerce.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, Long> {
    Optional<TenantDomain> findByDomain(String domain);
}
