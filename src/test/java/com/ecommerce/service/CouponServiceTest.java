package com.ecommerce.service;

import com.ecommerce.dto.request.CouponRequest;
import com.ecommerce.dto.response.CouponResponse;
import com.ecommerce.entity.Coupon;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService")
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;

    @InjectMocks private CouponService couponService;

    private Coupon activeCoupon;

    @BeforeEach
    void setUp() {
        activeCoupon = Coupon.builder()
                .id(1L)
                .code("SAVE10")
                .discountPercent(BigDecimal.TEN)
                .expiresAt(LocalDate.now().plusYears(1))
                .active(true)
                .usageCount(0)
                .usageLimit(null)
                .build();
    }

    // ─── getAllCoupons ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllCoupons")
    class GetAllCoupons {

        @Test
        @DisplayName("returns mapped list of all coupons")
        void getAllCoupons_success() {
            when(couponRepository.findAll()).thenReturn(List.of(activeCoupon));

            List<CouponResponse> result = couponService.getAllCoupons();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).code()).isEqualTo("SAVE10");
        }

        @Test
        @DisplayName("returns empty list when no coupons exist")
        void getAllCoupons_empty() {
            when(couponRepository.findAll()).thenReturn(List.of());

            assertThat(couponService.getAllCoupons()).isEmpty();
        }
    }

    // ─── createCoupon ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCoupon")
    class CreateCoupon {

        @Test
        @DisplayName("creates and returns coupon with uppercased code")
        void createCoupon_success() {
            CouponRequest request = new CouponRequest(
                    "save20", BigDecimal.valueOf(20), LocalDate.now().plusMonths(3), null);

            when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.empty());
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
                Coupon c = inv.getArgument(0);
                c.setId(2L);
                return c;
            });

            CouponResponse response = couponService.createCoupon(request);

            assertThat(response.code()).isEqualTo("SAVE20");
            assertThat(response.discountPercent()).isEqualByComparingTo(BigDecimal.valueOf(20));
        }

        @Test
        @DisplayName("throws BadRequestException when code already exists")
        void createCoupon_duplicateCode() {
            CouponRequest request = new CouponRequest(
                    "SAVE10", BigDecimal.TEN, LocalDate.now().plusMonths(1), null);

            when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(activeCoupon));

            assertThatThrownBy(() -> couponService.createCoupon(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("trims whitespace from code before saving")
        void createCoupon_trimsCode() {
            CouponRequest request = new CouponRequest(
                    "  PROMO  ", BigDecimal.valueOf(15), LocalDate.now().plusMonths(1), null);

            when(couponRepository.findByCode("PROMO")).thenReturn(Optional.empty());
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

            CouponResponse response = couponService.createCoupon(request);

            assertThat(response.code()).isEqualTo("PROMO");
        }
    }

    // ─── updateCoupon ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCoupon")
    class UpdateCoupon {

        @Test
        @DisplayName("updates coupon fields successfully")
        void updateCoupon_success() {
            CouponRequest request = new CouponRequest(
                    "SAVE10", BigDecimal.valueOf(25), LocalDate.now().plusYears(2), 50);

            when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));
            when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(activeCoupon));
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

            CouponResponse response = couponService.updateCoupon(1L, request);

            assertThat(response.discountPercent()).isEqualByComparingTo(BigDecimal.valueOf(25));
            assertThat(response.usageLimit()).isEqualTo(50);
        }

        @Test
        @DisplayName("throws BadRequestException when code is already used by a different coupon")
        void updateCoupon_codeConflict() {
            Coupon other = Coupon.builder().id(99L).code("SAVE10").build();
            CouponRequest request = new CouponRequest(
                    "SAVE10", BigDecimal.TEN, LocalDate.now().plusYears(1), null);

            when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));
            when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(other));

            assertThatThrownBy(() -> couponService.updateCoupon(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Code already used by another coupon");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when coupon does not exist")
        void updateCoupon_notFound() {
            CouponRequest request = new CouponRequest(
                    "X", BigDecimal.ONE, LocalDate.now().plusDays(1), null);

            when(couponRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.updateCoupon(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── deleteCoupon ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteCoupon")
    class DeleteCoupon {

        @Test
        @DisplayName("deletes the coupon when it exists")
        void deleteCoupon_success() {
            when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));

            couponService.deleteCoupon(1L);

            verify(couponRepository).delete(activeCoupon);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when coupon does not exist")
        void deleteCoupon_notFound() {
            when(couponRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.deleteCoupon(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(couponRepository, never()).delete(any());
        }
    }

    // ─── toggleCoupon ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleCoupon")
    class ToggleCoupon {

        @Test
        @DisplayName("sets inactive when coupon is currently active")
        void toggleCoupon_activeToInactive() {
            when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));
            when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CouponResponse response = couponService.toggleCoupon(1L);

            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("sets active when coupon is currently inactive")
        void toggleCoupon_inactiveToActive() {
            activeCoupon.setActive(false);

            when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));
            when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CouponResponse response = couponService.toggleCoupon(1L);

            assertThat(response.active()).isTrue();
        }
    }

    // ─── validateCoupon ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateCoupon")
    class ValidateCoupon {

        @Test
        @DisplayName("returns coupon response for a valid active coupon")
        void validateCoupon_success() {
            when(couponRepository.findByCodeIgnoreCase("save10")).thenReturn(Optional.of(activeCoupon));

            CouponResponse response = couponService.validateCoupon("save10");

            assertThat(response.code()).isEqualTo("SAVE10");
            assertThat(response.discountPercent()).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("throws BadRequestException when code does not exist")
        void validateCoupon_notFound() {
            when(couponRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.validateCoupon("NOPE"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid coupon code");
        }

        @Test
        @DisplayName("throws BadRequestException when coupon is inactive")
        void validateCoupon_inactive() {
            activeCoupon.setActive(false);
            when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(activeCoupon));

            assertThatThrownBy(() -> couponService.validateCoupon("SAVE10"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("This coupon is no longer active");
        }

        @Test
        @DisplayName("throws BadRequestException when coupon has expired")
        void validateCoupon_expired() {
            activeCoupon.setExpiresAt(LocalDate.now().minusDays(1));
            when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(activeCoupon));

            assertThatThrownBy(() -> couponService.validateCoupon("SAVE10"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("This coupon has expired");
        }

        @Test
        @DisplayName("throws BadRequestException when usage limit is reached")
        void validateCoupon_usageLimitReached() {
            activeCoupon.setUsageLimit(5);
            activeCoupon.setUsageCount(5);
            when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(activeCoupon));

            assertThatThrownBy(() -> couponService.validateCoupon("SAVE10"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("This coupon has reached its usage limit");
        }

        @Test
        @DisplayName("succeeds when usageLimit is null (unlimited)")
        void validateCoupon_unlimitedUsage() {
            activeCoupon.setUsageLimit(null);
            activeCoupon.setUsageCount(9999);
            when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(activeCoupon));

            assertThatNoException().isThrownBy(() -> couponService.validateCoupon("SAVE10"));
        }

        @Test
        @DisplayName("succeeds when usageCount is below the limit")
        void validateCoupon_belowLimit() {
            activeCoupon.setUsageLimit(10);
            activeCoupon.setUsageCount(4);
            when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(activeCoupon));

            assertThatNoException().isThrownBy(() -> couponService.validateCoupon("SAVE10"));
        }
    }

    // ─── consumeCoupon ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("consumeCoupon")
    class ConsumeCoupon {

        @Test
        @DisplayName("increments usageCount by 1 and saves")
        void consumeCoupon_success() {
            activeCoupon.setUsageCount(3);
            when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(activeCoupon));
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

            Coupon result = couponService.consumeCoupon("SAVE10");

            assertThat(result.getUsageCount()).isEqualTo(4);
            verify(couponRepository).save(activeCoupon);
        }

        @Test
        @DisplayName("throws BadRequestException when code does not exist")
        void consumeCoupon_notFound() {
            when(couponRepository.findByCodeIgnoreCase("GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.consumeCoupon("GHOST"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid coupon code");
        }
    }
}
