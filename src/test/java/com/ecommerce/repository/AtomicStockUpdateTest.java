package com.ecommerce.repository;

import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the conditional UPDATE queries against the real (H2) database. */
@DataJpaTest
@DisplayName("Atomic stock / coupon updates")
class AtomicStockUpdateTest {

    @Autowired TestEntityManager em;
    @Autowired ProductRepository productRepository;
    @Autowired CouponRepository couponRepository;

    private Product persistProduct(int stock) {
        return em.persistAndFlush(Product.builder()
                .name("Gorra").slug("gorra-" + System.nanoTime()).sku("SKU-" + System.nanoTime())
                .price(BigDecimal.TEN).stockQuantity(stock).active(true).build());
    }

    private int stockOf(Long productId) {
        em.clear();
        return em.find(Product.class, productId).getStockQuantity();
    }

    @Test
    @DisplayName("decrementStock takes units while there is enough")
    void decrement_enoughStock() {
        Product p = persistProduct(3);

        assertThat(productRepository.decrementStock(p.getId(), 2)).isEqualTo(1);
        assertThat(stockOf(p.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("decrementStock refuses (0 rows) and never goes negative when stock ran out")
    void decrement_lastUnitOnlyOnce() {
        Product p = persistProduct(1);

        // Two orders for the last unit: only the first one wins
        assertThat(productRepository.decrementStock(p.getId(), 1)).isEqualTo(1);
        assertThat(productRepository.decrementStock(p.getId(), 1)).isZero();
        assertThat(stockOf(p.getId())).isZero();
    }

    @Test
    @DisplayName("incrementStock gives units back")
    void increment() {
        Product p = persistProduct(0);

        productRepository.incrementStock(p.getId(), 4);
        assertThat(stockOf(p.getId())).isEqualTo(4);
    }

    @Test
    @DisplayName("incrementUsageIfAvailable stops at the coupon usage limit")
    void couponLimit() {
        Coupon c = new Coupon();
        c.setCode("LIMIT2");
        c.setDiscountPercent(BigDecimal.TEN);
        c.setExpiresAt(LocalDate.now().plusDays(5));
        c.setActive(true);
        c.setUsageLimit(2);
        c = em.persistAndFlush(c);

        assertThat(couponRepository.incrementUsageIfAvailable(c.getId())).isEqualTo(1);
        assertThat(couponRepository.incrementUsageIfAvailable(c.getId())).isEqualTo(1);
        assertThat(couponRepository.incrementUsageIfAvailable(c.getId())).isZero();
        em.clear();
        assertThat(em.find(Coupon.class, c.getId()).getUsageCount()).isEqualTo(2);
    }
}
