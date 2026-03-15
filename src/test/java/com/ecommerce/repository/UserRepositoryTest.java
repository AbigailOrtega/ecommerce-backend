package com.ecommerce.repository;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@DisplayName("UserRepository")
class UserRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = em.persistAndFlush(User.builder()
                .firstName("Ana").lastName("López")
                .email("ana@example.com").password("encoded")
                .role(Role.CUSTOMER).enabled(true).build());
    }

    // ─── findByEmail ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("returns user when email matches")
        void findByEmail_found() {
            Optional<User> result = userRepository.findByEmail("ana@example.com");
            assertThat(result).isPresent();
            assertThat(result.get().getFirstName()).isEqualTo("Ana");
        }

        @Test
        @DisplayName("returns empty when email does not match")
        void findByEmail_notFound() {
            Optional<User> result = userRepository.findByEmail("ghost@example.com");
            assertThat(result).isEmpty();
        }
    }

    // ─── existsByEmail ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByEmail")
    class ExistsByEmail {

        @Test
        @DisplayName("returns true when email exists")
        void existsByEmail_true() {
            assertThat(userRepository.existsByEmail("ana@example.com")).isTrue();
        }

        @Test
        @DisplayName("returns false when email does not exist")
        void existsByEmail_false() {
            assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
        }
    }

    // ─── findByResetToken ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByResetToken")
    class FindByResetToken {

        @Test
        @DisplayName("returns user when token matches")
        void findByResetToken_found() {
            savedUser.setResetToken("my-reset-token");
            em.persistAndFlush(savedUser);

            Optional<User> result = userRepository.findByResetToken("my-reset-token");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("ana@example.com");
        }

        @Test
        @DisplayName("returns empty when token does not match")
        void findByResetToken_notFound() {
            Optional<User> result = userRepository.findByResetToken("wrong-token");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when no user has a reset token")
        void findByResetToken_noTokenSet() {
            Optional<User> result = userRepository.findByResetToken("any-token");
            assertThat(result).isEmpty();
        }
    }
}
