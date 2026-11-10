package com.ecommerce.service;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAccountService")
class AdminAccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @InjectMocks AdminAccountService service;

    private static User user(long id, String email, Role role) {
        return User.builder().id(id).email(email).firstName("Ana").lastName("López").password("hash").role(role).build();
    }

    @Nested
    @DisplayName("bootstrap (ADMIN_EMAIL)")
    class Bootstrap {

        @Test
        @DisplayName("does nothing once the store has an admin")
        void noOpWhenAdminExists() {
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

            assertThat(service.bootstrap("owner@shop.com")).isFalse();

            verify(userRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("promotes the existing account with that email, matching case-insensitively")
        void promotesExisting() {
            User existing = user(7, "Owner@Shop.com", Role.CUSTOMER);
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
            when(userRepository.findFirstByEmailIgnoreCase("owner@shop.com")).thenReturn(Optional.of(existing));

            assertThat(service.bootstrap(" owner@shop.com ")).isTrue();

            assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
            verify(userRepository).save(existing);
            verify(emailService).sendAdminInvitationEmail(eq("Owner@Shop.com"), eq("Ana"), isNull());
        }

        @Test
        @DisplayName("creates the account with no usable password and emails a 48h link to set one")
        void createsNew() {
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
            when(userRepository.findFirstByEmailIgnoreCase("owner@shop.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("random-hash");

            assertThat(service.bootstrap("owner@shop.com")).isTrue();

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            User created = saved.getValue();
            assertThat(created.getRole()).isEqualTo(Role.ADMIN);
            assertThat(created.getEmail()).isEqualTo("owner@shop.com");
            assertThat(created.getPassword()).isEqualTo("random-hash");
            assertThat(created.getResetToken()).isNotBlank();
            assertThat(created.getResetTokenExpiry())
                    .isAfter(LocalDateTime.now().plusHours(47))
                    .isBefore(LocalDateTime.now().plusHours(49));
            verify(emailService).sendAdminInvitationEmail("owner@shop.com", "Admin", created.getResetToken());
        }
    }

    @Nested
    @DisplayName("invite")
    class Invite {

        @Test
        @DisplayName("an existing customer is promoted and told, without a password link")
        void promotesCustomer() {
            User customer = user(3, "ana@shop.com", Role.CUSTOMER);
            when(userRepository.findFirstByEmailIgnoreCase("ana@shop.com")).thenReturn(Optional.of(customer));

            User result = service.invite("ana@shop.com", null, null, "boss@shop.com");

            assertThat(result.getRole()).isEqualTo(Role.ADMIN);
            assertThat(result.getResetToken()).isNull();
            verify(emailService).sendAdminInvitationEmail(eq("ana@shop.com"), eq("Ana"), isNull());
        }

        @Test
        @DisplayName("a new email gets an account named as given")
        void createsWithNames() {
            when(userRepository.findFirstByEmailIgnoreCase("new@shop.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("random-hash");

            User result = service.invite("new@shop.com", " Sofía ", "Ruiz", "boss@shop.com");

            assertThat(result.getFirstName()).isEqualTo("Sofía");
            assertThat(result.getLastName()).isEqualTo("Ruiz");
            assertThat(result.getRole()).isEqualTo(Role.ADMIN);
            verify(emailService).sendAdminInvitationEmail("new@shop.com", "Sofía", result.getResetToken());
        }

        @Test
        @DisplayName("someone who is already admin → 400")
        void alreadyAdmin() {
            when(userRepository.findFirstByEmailIgnoreCase("boss2@shop.com"))
                    .thenReturn(Optional.of(user(4, "boss2@shop.com", Role.ADMIN)));

            assertThatThrownBy(() -> service.invite("boss2@shop.com", null, null, "boss@shop.com"))
                    .isInstanceOf(BadRequestException.class);
            verify(userRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("turns another admin back into a customer")
        void revokes() {
            User other = user(5, "other@shop.com", Role.ADMIN);
            when(userRepository.findById(5L)).thenReturn(Optional.of(other));
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);
            when(userRepository.save(other)).thenReturn(other);

            assertThat(service.revoke(5L, "boss@shop.com").getRole()).isEqualTo(Role.CUSTOMER);
        }

        @Test
        @DisplayName("never your own access")
        void notSelf() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, "Boss@shop.com", Role.ADMIN)));

            assertThatThrownBy(() -> service.revoke(1L, "boss@shop.com")).isInstanceOf(BadRequestException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("never the last admin")
        void notLast() {
            when(userRepository.findById(5L)).thenReturn(Optional.of(user(5, "other@shop.com", Role.ADMIN)));
            when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> service.revoke(5L, "boss@shop.com"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("al menos un admin");
        }

        @Test
        @DisplayName("an account that is not admin → 400")
        void notAdmin() {
            when(userRepository.findById(6L)).thenReturn(Optional.of(user(6, "c@shop.com", Role.CUSTOMER)));

            assertThatThrownBy(() -> service.revoke(6L, "boss@shop.com")).isInstanceOf(BadRequestException.class);
        }
    }
}
