package com.ecommerce.repository;

import com.ecommerce.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    Optional<User> findByUnsubscribeToken(String unsubscribeToken);
    java.util.List<User> findAllByMarketingOptInTrue();
    long countByMarketingOptInTrue();

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<User> search(@Param("q") String q, Pageable pageable);
}
