package com.ecommerce.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends TenantScoped implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    private String lastName;

    @Email
    @NotBlank
    @Column(nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password;

    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Builder.Default
    private boolean enabled = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    @Builder.Default
    private boolean marketingOptIn = false;

    @Column(unique = true)
    private String unsubscribeToken;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // a super admin is also an admin of its store (#1)
        if (role == Role.SUPER_ADMIN) {
            return List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
