package com.ecommerce.service;

import com.ecommerce.dto.request.CouponRequest;
import com.ecommerce.dto.response.CouponResponse;
import com.ecommerce.entity.Coupon;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        String code = request.code().toUpperCase().trim();
        if (couponRepository.findByCode(code).isPresent()) {
            throw new BadRequestException("Coupon code already exists: " + code);
        }
        Coupon coupon = Coupon.builder()
                .code(code)
                .discountPercent(request.discountPercent())
                .expiresAt(request.expiresAt())
                .usageLimit(request.usageLimit())
                .active(true)
                .build();
        return mapToResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse updateCoupon(Long id, CouponRequest request) {
        Coupon coupon = findById(id);
        String code = request.code().toUpperCase().trim();
        couponRepository.findByCode(code).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw new BadRequestException("Code already used by another coupon");
        });
        coupon.setCode(code);
        coupon.setDiscountPercent(request.discountPercent());
        coupon.setExpiresAt(request.expiresAt());
        coupon.setUsageLimit(request.usageLimit());
        return mapToResponse(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponRepository.delete(findById(id));
    }

    @Transactional
    public CouponResponse toggleCoupon(Long id) {
        Coupon coupon = findById(id);
        coupon.setActive(!coupon.isActive());
        return mapToResponse(couponRepository.save(coupon));
    }

    /** Called by users to validate a code before checkout. Returns coupon if valid. */
    public CouponResponse validateCoupon(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));

        if (!coupon.isActive()) {
            throw new BadRequestException("This coupon is no longer active");
        }
        if (coupon.getExpiresAt().isBefore(LocalDate.now())) {
            throw new BadRequestException("This coupon has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsageCount() >= coupon.getUsageLimit()) {
            throw new BadRequestException("This coupon has reached its usage limit");
        }
        return mapToResponse(coupon);
    }

    /** Called internally by OrderService when placing an order. */
    @Transactional
    public Coupon consumeCoupon(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));
        coupon.setUsageCount(coupon.getUsageCount() + 1);
        return couponRepository.save(coupon);
    }

    private Coupon findById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", id));
    }

    public CouponResponse mapToResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountPercent(),
                coupon.getExpiresAt(),
                coupon.isActive(),
                coupon.getUsageCount(),
                coupon.getUsageLimit(),
                coupon.getCreatedAt()
        );
    }
}
