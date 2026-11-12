package com.ecommerce.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);

    Optional<PlatformAdmin> findByResetToken(String resetToken);
}
