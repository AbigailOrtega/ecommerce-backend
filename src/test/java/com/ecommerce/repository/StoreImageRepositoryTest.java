package com.ecommerce.repository;

import com.ecommerce.entity.StoreImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@DisplayName("StoreImageRepository")
class StoreImageRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired StoreImageRepository repository;

    // ─── findAllByActiveTrueOrderByDisplayOrderAsc ────────────────────────────

    @Nested
    @DisplayName("findAllByActiveTrueOrderByDisplayOrderAsc")
    class FindAllActiveOrdered {

        @Test
        @DisplayName("returns only active images sorted ascending by displayOrder")
        void returnsActiveImagesInOrder() {
            em.persistAndFlush(StoreImage.builder().url("img-order3").displayOrder(3).active(true).build());
            em.persistAndFlush(StoreImage.builder().url("img-order1").displayOrder(1).active(true).build());
            em.persistAndFlush(StoreImage.builder().url("img-inactive").displayOrder(2).active(false).build());

            List<StoreImage> result = repository.findAllByActiveTrueOrderByDisplayOrderAsc();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUrl()).isEqualTo("img-order1");
            assertThat(result.get(1).getUrl()).isEqualTo("img-order3");
        }

        @Test
        @DisplayName("excludes inactive images")
        void excludesInactiveImages() {
            em.persistAndFlush(StoreImage.builder().url("active").displayOrder(1).active(true).build());
            em.persistAndFlush(StoreImage.builder().url("inactive").displayOrder(2).active(false).build());

            List<StoreImage> result = repository.findAllByActiveTrueOrderByDisplayOrderAsc();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUrl()).isEqualTo("active");
        }

        @Test
        @DisplayName("returns empty list when all images are inactive")
        void returnsEmptyWhenAllInactive() {
            em.persistAndFlush(StoreImage.builder().url("img").displayOrder(1).active(false).build());

            List<StoreImage> result = repository.findAllByActiveTrueOrderByDisplayOrderAsc();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no images at all")
        void returnsEmptyWhenNoImages() {
            List<StoreImage> result = repository.findAllByActiveTrueOrderByDisplayOrderAsc();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns all active images when all have same displayOrder")
        void returnsAllWhenSameOrder() {
            em.persistAndFlush(StoreImage.builder().url("imgA").displayOrder(1).active(true).build());
            em.persistAndFlush(StoreImage.builder().url("imgB").displayOrder(1).active(true).build());

            List<StoreImage> result = repository.findAllByActiveTrueOrderByDisplayOrderAsc();

            assertThat(result).hasSize(2);
        }
    }
}
