package com.ecommerce.credentials;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** One row per store (Hibernate's tenant filter picks the current store's). */
public interface StoreCredentialsRepository extends JpaRepository<StoreCredentials, Long> {

    Optional<StoreCredentials> findFirstByOrderByIdAsc();
}
