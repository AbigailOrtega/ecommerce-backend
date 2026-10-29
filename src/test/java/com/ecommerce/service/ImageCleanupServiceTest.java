package com.ecommerce.service;

import com.ecommerce.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import(ImageCleanupService.class)
@DisplayName("ImageCleanupService")
class ImageCleanupServiceTest {

    @Autowired ImageCleanupService cleanup;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @MockBean CloudinaryService cloudinaryService;

    private Product product(String mainImage, List<String> images) {
        Product p = Product.builder().name("P").price(BigDecimal.TEN).imageUrl(mainImage).images(new ArrayList<>(images)).build();
        em.persist(p);
        return p;
    }

    @Test
    @DisplayName("an image is referenced while any product, color, category, banner or store record uses it")
    void isReferenced_everyImageColumn() {
        Product p = product("u-main", List.of("u-gallery"));
        ProductColor color = ProductColor.builder().product(p).name("Rojo").images(new ArrayList<>(List.of("u-color"))).build();
        em.persist(color);
        em.persist(Category.builder().name("C").imageUrl("u-category").build());
        em.persist(PromoBanner.builder().imageUrl("u-banner").build());
        em.persist(StoreImage.builder().url("u-store").displayOrder(1).active(true).build());
        em.persist(StoreInfo.builder().id(1L).logoUrl("u-logo").build());
        em.flush();

        for (String url : List.of("u-main", "u-gallery", "u-color", "u-category", "u-banner", "u-store", "u-logo")) {
            assertThat(cleanup.isReferenced(url)).as(url).isTrue();
        }
        assertThat(cleanup.isReferenced("u-unused")).isFalse();
    }

    @Test
    @DisplayName("deleteIfUnused deletes unused images and keeps used ones")
    void deleteIfUnused() {
        product("u-used", List.of());
        em.flush();

        assertThat(cleanup.deleteIfUnused("u-used")).isFalse();
        assertThat(cleanup.deleteIfUnused("u-free")).isTrue();

        verify(cloudinaryService).delete("u-free");
        verify(cloudinaryService, never()).delete("u-used");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("deleteRemoved only considers images that were removed by the edit (no transaction: right away)")
    void deleteRemoved() {
        cleanup.deleteRemoved(List.of("u-kept", "u-removed"), List.of("u-kept", "u-new"));

        verify(cloudinaryService).delete("u-removed");
        verifyNoMoreInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("a Cloudinary failure never breaks the caller")
    void cloudinaryFailure() {
        doThrow(new RuntimeException("down")).when(cloudinaryService).delete("u-x");
        assertThat(cleanup.deleteIfUnused("u-x")).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("inside a transaction, deletes only after commit, and never if it rolls back")
    void afterCommitOnly() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            cleanup.deleteUnused(List.of("u-rolled-back"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cloudinaryService);

        tx.executeWithoutResult(s -> {
            cleanup.deleteUnused(List.of("u-committed"));
            verifyNoInteractions(cloudinaryService);
        });
        verify(cloudinaryService).delete("u-committed");
    }
}
