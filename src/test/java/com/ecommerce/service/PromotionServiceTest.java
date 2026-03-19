package com.ecommerce.service;

import com.ecommerce.dto.request.PromotionRequest;
import com.ecommerce.dto.response.PromotionResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Promotion;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionService")
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private PromotionService promotionService;

    private Product productA;
    private Product productB;
    private Promotion activePromotion;

    private static final LocalDate START = LocalDate.of(2025, 6, 1);
    private static final LocalDate END   = LocalDate.of(2025, 6, 30);

    @BeforeEach
    void setUp() {
        productA = Product.builder()
                .id(10L)
                .name("Sneaker")
                .price(new BigDecimal("99.99"))
                .build();

        productB = Product.builder()
                .id(20L)
                .name("T-Shirt")
                .price(new BigDecimal("29.99"))
                .build();

        activePromotion = Promotion.builder()
                .id(1L)
                .name("Summer Sale")
                .discountPercent(new BigDecimal("20.00"))
                .startDate(START)
                .endDate(END)
                .active(true)
                .products(Set.of(productA))
                .build();
    }

    // ─── getAllPromotions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllPromotions")
    class GetAllPromotions {

        @Test
        @DisplayName("returns a mapped response for every promotion in the repository")
        void getAllPromotions_returnsMappedList() {
            when(promotionRepository.findAll()).thenReturn(List.of(activePromotion));

            List<PromotionResponse> result = promotionService.getAllPromotions();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Summer Sale");
        }

        @Test
        @DisplayName("returns empty list when no promotions exist")
        void getAllPromotions_returnsEmptyList() {
            when(promotionRepository.findAll()).thenReturn(List.of());

            assertThat(promotionService.getAllPromotions()).isEmpty();
        }

        @Test
        @DisplayName("maps all scalar fields of each promotion correctly")
        void getAllPromotions_mapsAllScalarFields() {
            when(promotionRepository.findAll()).thenReturn(List.of(activePromotion));

            PromotionResponse response = promotionService.getAllPromotions().get(0);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Summer Sale");
            assertThat(response.discountPercent()).isEqualByComparingTo("20.00");
            assertThat(response.startDate()).isEqualTo(START);
            assertThat(response.endDate()).isEqualTo(END);
            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("maps nested product summaries for each promotion")
        void getAllPromotions_mapsProductSummaries() {
            when(promotionRepository.findAll()).thenReturn(List.of(activePromotion));

            PromotionResponse response = promotionService.getAllPromotions().get(0);

            assertThat(response.products()).hasSize(1);
            PromotionResponse.PromotionProductSummary summary = response.products().get(0);
            assertThat(summary.id()).isEqualTo(10L);
            assertThat(summary.name()).isEqualTo("Sneaker");
            assertThat(summary.price()).isEqualByComparingTo("99.99");
        }

        @Test
        @DisplayName("returns promotion with empty products list when no products are linked")
        void getAllPromotions_returnsEmptyProductsWhenNoneLinked() {
            Promotion noProducts = Promotion.builder()
                    .id(2L).name("Empty Promo").discountPercent(BigDecimal.TEN)
                    .startDate(START).endDate(END).active(true).products(Set.of()).build();
            when(promotionRepository.findAll()).thenReturn(List.of(noProducts));

            PromotionResponse response = promotionService.getAllPromotions().get(0);

            assertThat(response.products()).isEmpty();
        }
    }

    // ─── createPromotion ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPromotion")
    class CreatePromotion {

        private PromotionRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new PromotionRequest(
                    "Black Friday", new BigDecimal("30.00"), START, END, List.of(10L, 20L));
        }

        @Test
        @DisplayName("saves and returns promotion when all productIds resolve to products")
        void createPromotion_success() {
            when(productRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(productA, productB));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> {
                Promotion p = inv.getArgument(0);
                p.setId(99L);
                return p;
            });

            PromotionResponse result = promotionService.createPromotion(validRequest);

            assertThat(result.id()).isEqualTo(99L);
            assertThat(result.name()).isEqualTo("Black Friday");
            assertThat(result.discountPercent()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when any productId is not found")
        void createPromotion_missingProduct() {
            // Only one product returned for two requested ids
            when(productRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(productA));

            assertThatThrownBy(() -> promotionService.createPromotion(validRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("One or more products not found");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when productIds list is empty but resolves none")
        void createPromotion_emptyProductsReturned() {
            PromotionRequest req = new PromotionRequest(
                    "Ghost", BigDecimal.TEN, START, END, List.of(999L));
            when(productRepository.findAllById(List.of(999L))).thenReturn(List.of());

            assertThatThrownBy(() -> promotionService.createPromotion(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("new promotion is always created as active")
        void createPromotion_isActiveByDefault() {
            when(productRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(productA, productB));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            PromotionResponse result = promotionService.createPromotion(validRequest);

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("persists correct name, discountPercent, dates, and products to repository")
        void createPromotion_persistsCorrectFields() {
            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            when(productRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(productA, productB));
            when(promotionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promotionService.createPromotion(validRequest);

            Promotion saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Black Friday");
            assertThat(saved.getDiscountPercent()).isEqualByComparingTo("30.00");
            assertThat(saved.getStartDate()).isEqualTo(START);
            assertThat(saved.getEndDate()).isEqualTo(END);
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getProducts()).containsExactlyInAnyOrder(productA, productB);
        }

        @Test
        @DisplayName("maps product summaries from linked products into the response")
        void createPromotion_mapsProductSummariesInResponse() {
            when(productRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(productA, productB));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            PromotionResponse result = promotionService.createPromotion(validRequest);

            assertThat(result.products()).hasSize(2);
            assertThat(result.products())
                    .extracting(PromotionResponse.PromotionProductSummary::id)
                    .containsExactlyInAnyOrder(10L, 20L);
        }
    }

    // ─── updatePromotion ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePromotion")
    class UpdatePromotion {

        private PromotionRequest updateRequest;

        @BeforeEach
        void setUp() {
            updateRequest = new PromotionRequest(
                    "Summer Sale Updated", new BigDecimal("25.00"),
                    START.plusDays(5), END.plusDays(5), List.of(20L));
        }

        @Test
        @DisplayName("updates all mutable fields and returns mapped response")
        void updatePromotion_success() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(productRepository.findAllById(List.of(20L))).thenReturn(List.of(productB));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            PromotionResponse result = promotionService.updatePromotion(1L, updateRequest);

            assertThat(result.name()).isEqualTo("Summer Sale Updated");
            assertThat(result.discountPercent()).isEqualByComparingTo("25.00");
            assertThat(result.startDate()).isEqualTo(START.plusDays(5));
            assertThat(result.endDate()).isEqualTo(END.plusDays(5));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when promotion does not exist")
        void updatePromotion_notFound() {
            when(promotionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.updatePromotion(999L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Promotion")
                    .hasMessageContaining("999");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when any productId is not found during update")
        void updatePromotion_missingProduct() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(productRepository.findAllById(List.of(20L))).thenReturn(List.of()); // none found

            assertThatThrownBy(() -> promotionService.updatePromotion(1L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("One or more products not found");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("replaces existing product set with new product set from request")
        void updatePromotion_replacesProductSet() {
            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(productRepository.findAllById(List.of(20L))).thenReturn(List.of(productB));
            when(promotionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promotionService.updatePromotion(1L, updateRequest);

            assertThat(captor.getValue().getProducts()).containsExactly(productB);
            assertThat(captor.getValue().getProducts()).doesNotContain(productA);
        }

        @Test
        @DisplayName("persists mutated entity with all updated values")
        void updatePromotion_persistsMutatedEntity() {
            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(productRepository.findAllById(List.of(20L))).thenReturn(List.of(productB));
            when(promotionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promotionService.updatePromotion(1L, updateRequest);

            Promotion saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Summer Sale Updated");
            assertThat(saved.getDiscountPercent()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("validates product count mismatch when only partial ids are found")
        void updatePromotion_partialProductMismatch() {
            PromotionRequest req = new PromotionRequest(
                    "Partial", BigDecimal.TEN, START, END, List.of(10L, 20L));
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(productRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(productA)); // only one

            assertThatThrownBy(() -> promotionService.updatePromotion(1L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── deletePromotion ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePromotion")
    class DeletePromotion {

        @Test
        @DisplayName("deletes the promotion when it exists")
        void deletePromotion_success() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));

            assertThatNoException().isThrownBy(() -> promotionService.deletePromotion(1L));

            verify(promotionRepository).delete(activePromotion);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when promotion does not exist")
        void deletePromotion_notFound() {
            when(promotionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.deletePromotion(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Promotion")
                    .hasMessageContaining("999");

            verify(promotionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deletes the exact entity instance returned by findById")
        void deletePromotion_deletesCorrectEntity() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));

            promotionService.deletePromotion(1L);

            verify(promotionRepository).delete(activePromotion);
        }

        @Test
        @DisplayName("does not call delete when the promotion is not found")
        void deletePromotion_noDeleteOnMissingId() {
            when(promotionRepository.findById(50L)).thenReturn(Optional.empty());

            try {
                promotionService.deletePromotion(50L);
            } catch (ResourceNotFoundException ignored) {
                // expected
            }

            verify(promotionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("calls findById before attempting deletion")
        void deletePromotion_lookupBeforeDelete() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));

            promotionService.deletePromotion(1L);

            verify(promotionRepository).findById(1L);
        }
    }

    // ─── togglePromotion ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("togglePromotion")
    class TogglePromotion {

        @Test
        @DisplayName("sets promotion to inactive when it is currently active")
        void togglePromotion_activeToInactive() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            PromotionResponse result = promotionService.togglePromotion(1L);

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("sets promotion to active when it is currently inactive")
        void togglePromotion_inactiveToActive() {
            activePromotion.setActive(false);
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            PromotionResponse result = promotionService.togglePromotion(1L);

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when promotion does not exist")
        void togglePromotion_notFound() {
            when(promotionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.togglePromotion(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Promotion")
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("persists the toggled state via save and verifies save is called once")
        void togglePromotion_persistsToggledState() {
            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(promotionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promotionService.togglePromotion(1L);

            assertThat(captor.getValue().isActive()).isFalse();
            verify(promotionRepository).save(any(Promotion.class));
        }

        @Test
        @DisplayName("returns full mapped response with all fields including product summaries after toggle")
        void togglePromotion_returnsMappedResponseWithProducts() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            PromotionResponse result = promotionService.togglePromotion(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Summer Sale");
            assertThat(result.products()).hasSize(1);
            assertThat(result.products().get(0).id()).isEqualTo(10L);
        }

        @Test
        @DisplayName("toggling twice restores the original active state")
        void togglePromotion_doubleToggleRestoresOriginalState() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(activePromotion));
            when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

            // First toggle: active → inactive
            PromotionResponse afterFirst = promotionService.togglePromotion(1L);
            assertThat(afterFirst.active()).isFalse();

            // activePromotion.active is now false (entity was mutated in place)
            // Second toggle: inactive → active
            PromotionResponse afterSecond = promotionService.togglePromotion(1L);
            assertThat(afterSecond.active()).isTrue();
        }
    }
}
