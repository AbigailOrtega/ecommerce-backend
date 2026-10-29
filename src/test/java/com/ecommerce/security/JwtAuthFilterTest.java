package com.ecommerce.security;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private UserDetailsService userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthFilter jwtAuthFilter;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtTokenProvider = JwtTokenProviderTest.newProvider();
        jwtAuthFilter = new JwtAuthFilter(jwtTokenProvider, userDetailsService);
        user = User.builder().email("user@test.com").password("$2a$10$hash").role(Role.CUSTOMER).enabled(true).build();
    }

    private void bearer(String token) {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    }

    private void assertAnonymous() throws Exception {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("autentica al usuario con un access token válido")
    void accessToken_setsAuthentication() throws Exception {
        bearer(jwtTokenProvider.generateAccessToken(user));
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(user);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user@test.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("no autentica con un refresh token")
    void refreshToken_noAuthentication() throws Exception {
        bearer(jwtTokenProvider.generateRefreshToken(user));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertAnonymous();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("no autentica con un token antiguo sin tipo (debe renovarse con /refresh)")
    void legacyToken_noAuthentication() throws Exception {
        bearer(JwtTokenProviderTest.legacyToken("user@test.com"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertAnonymous();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("no autentica si la contraseña cambió después de emitir el token")
    void passwordChanged_noAuthentication() throws Exception {
        bearer(jwtTokenProvider.generateAccessToken(user));
        User changed = User.builder().email("user@test.com").password("$2a$10$other").role(Role.CUSTOMER).build();
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(changed);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertAnonymous();
    }

    @Test
    @DisplayName("no falla si el usuario del token ya no existe")
    void deletedUser_noAuthentication() throws Exception {
        bearer(jwtTokenProvider.generateAccessToken(user));
        when(userDetailsService.loadUserByUsername("user@test.com")).thenThrow(new UsernameNotFoundException("gone"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertAnonymous();
    }

    @Test
    @DisplayName("no autentica cuando el token es inválido")
    void invalidToken_noAuthentication() throws Exception {
        bearer("bad.token");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertAnonymous();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("no autentica cuando no hay header Authorization o no es Bearer")
    void noBearer_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null, "Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, times(2)).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }
}
