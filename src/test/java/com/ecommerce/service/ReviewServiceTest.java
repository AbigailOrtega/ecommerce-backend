package com.ecommerce.service;

import com.ecommerce.dto.request.ReviewRequest;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.dto.response.ReviewSummaryResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService")
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private ReviewService reviewService;

    private User customer;
    private User admin;
    private Product product;
    private Review review;
    private Order deliveredOrder;
    private OrderItem orderItemForProduct;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(10L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .password("hashed")
                .role(Role.CUSTOMER)
                .build();

        admin = User.builder()
                .id(99L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@example.com")
                .password("hashed")
                .role(Role.ADMIN)
                .build();

        product = Product.builder()
                .id(1L)
                .name("Blue Hoodie")
                .price(BigDecimal.valueOf(59.99))
                .categories(new ArrayList<>())
                .colors(new ArrayList<>())
                .build();

        orderItemForProduct = OrderItem.builder()
                .id(100L)
                .product(product)
                .productName("Blue Hoodie")
                .productPrice(BigDecimal.valueOf(59.99))
                .quantity(1)
                .subtotal(BigDecimal.valueOf(59.99))
                .build();

        deliveredOrder = Order.builder()
                .id(50L)
                .orderNumber("ORD-ABC123")
                .user(customer)
                .totalAmount(BigDecimal.valueOf(59.99))
                .status(OrderStatus.DELIVERED)
                .shippingAddress("123 Main St")
                .shippingCity("Monterrey")
                .shippingState("NL")
                .shippingZipCode("64000")
                .shippingCountry("Mexico")
                .items(new ArrayList<>(List.of(orderItemForProduct)))
                .build();

        review = Review.builder()
                .id(200L)
                .product(product)
                .user(customer)
                .rating(5)
                .title("Excellent!")
                .comment("Really loved this product.")
                .verified(true)
                .approved(false)
                .build();
    }

    // ─── createReview ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createReview")
    class CreateReview {

        private ReviewRequest validRequest() {
            return new ReviewRequest(5, "Excellent!", "Really loved this product.");
        }

        @Test
        @DisplayName("creates and returns review for a verified purchaser")
        void createReview_success() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(deliveredOrder));
            when(reviewRepository.existsByProductIdAndUserId(1L, 10L)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(200L);
                return r;
            });

            ReviewResponse result = reviewService.createReview("jane@example.com", 1L, validRequest());

            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getRating()).isEqualTo(5);
            assertThat(result.getTitle()).isEqualTo("Excellent!");
            assertThat(result.isVerified()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user email does not exist")
        void createReview_userNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.createReview("ghost@example.com", 1L, validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("email");

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void createReview_productNotFound() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.createReview("jane@example.com", 999L, validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("id");

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccessDeniedException when user has not purchased the product")
        void createReview_notPurchased() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            // No orders at all
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

            assertThatThrownBy(() -> reviewService.createReview("jane@example.com", 1L, validRequest()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("purchased");

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccessDeniedException when the matching order is not DELIVERED")
        void createReview_orderNotDelivered() {
            Order pendingOrder = Order.builder()
                    .id(51L)
                    .orderNumber("ORD-XYZ")
                    .user(customer)
                    .totalAmount(BigDecimal.valueOf(59.99))
                    .status(OrderStatus.SHIPPED)
                    .shippingAddress("123 Main").shippingCity("MTY")
                    .shippingState("NL").shippingZipCode("64000").shippingCountry("MX")
                    .items(new ArrayList<>(List.of(orderItemForProduct)))
                    .build();

            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(pendingOrder));

            assertThatThrownBy(() -> reviewService.createReview("jane@example.com", 1L, validRequest()))
                    .isInstanceOf(AccessDeniedException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BadRequestException when user has already reviewed the product")
        void createReview_alreadyReviewed() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(deliveredOrder));
            when(reviewRepository.existsByProductIdAndUserId(1L, 10L)).thenReturn(true);

            assertThatThrownBy(() -> reviewService.createReview("jane@example.com", 1L, validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already reviewed");

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("saves review with verified=true and approved=false by default")
        void createReview_setsVerifiedAndNotApproved() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(deliveredOrder));
            when(reviewRepository.existsByProductIdAndUserId(1L, 10L)).thenReturn(false);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            when(reviewRepository.save(captor.capture())).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(201L);
                return r;
            });

            reviewService.createReview("jane@example.com", 1L, validRequest());

            assertThat(captor.getValue().isVerified()).isTrue();
            assertThat(captor.getValue().isApproved()).isFalse();
        }

        @Test
        @DisplayName("maps user full name correctly in response")
        void createReview_mapsUserName() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(deliveredOrder));
            when(reviewRepository.existsByProductIdAndUserId(1L, 10L)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(202L);
                return r;
            });

            ReviewResponse result = reviewService.createReview("jane@example.com", 1L, validRequest());

            assertThat(result.getUserName()).isEqualTo("Jane Doe");
        }
    }

    // ─── getProductReviews ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductReviews")
    class GetProductReviews {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("returns page of approved reviews for an existing product")
        void getProductReviews_success() {
            review.setApproved(true);
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findByProductIdAndApprovedTrue(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(review)));

            Page<ReviewResponse> result = reviewService.getProductReviews(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getRating()).isEqualTo(5);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void getProductReviews_productNotFound() {
            when(productRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> reviewService.getProductReviews(999L, pageable))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(reviewRepository, never()).findByProductIdAndApprovedTrue(any(), any());
        }

        @Test
        @DisplayName("returns empty page when product exists but has no approved reviews")
        void getProductReviews_noApprovedReviews() {
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findByProductIdAndApprovedTrue(1L, pageable))
                    .thenReturn(Page.empty());

            assertThat(reviewService.getProductReviews(1L, pageable).getContent()).isEmpty();
        }

        @Test
        @DisplayName("passes correct productId and pageable to repository")
        void getProductReviews_passesArgsToRepo() {
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findByProductIdAndApprovedTrue(1L, pageable)).thenReturn(Page.empty());

            reviewService.getProductReviews(1L, pageable);

            verify(reviewRepository).findByProductIdAndApprovedTrue(1L, pageable);
        }

        @Test
        @DisplayName("maps all fields of a review to response correctly")
        void getProductReviews_mapsResponseFields() {
            review.setApproved(true);
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findByProductIdAndApprovedTrue(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(review)));

            ReviewResponse r = reviewService.getProductReviews(1L, pageable).getContent().get(0);

            assertThat(r.getId()).isEqualTo(200L);
            assertThat(r.getComment()).isEqualTo("Really loved this product.");
            assertThat(r.isApproved()).isTrue();
            assertThat(r.isVerified()).isTrue();
        }
    }

    // ─── getProductSummary ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductSummary")
    class GetProductSummary {

        @Test
        @DisplayName("returns summary with average rating and total reviews")
        void getProductSummary_success() {
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findAverageRatingByProductId(1L)).thenReturn(4.5);
            when(reviewRepository.countByProductIdAndApprovedTrue(1L)).thenReturn(10L);
            when(reviewRepository.findRatingDistributionByProductId(1L)).thenReturn(List.of(
                    new Object[]{5, 7L},
                    new Object[]{4, 3L}
            ));

            ReviewSummaryResponse result = reviewService.getProductSummary(1L);

            assertThat(result.getAverageRating()).isEqualTo(4.5);
            assertThat(result.getTotalReviews()).isEqualTo(10);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void getProductSummary_productNotFound() {
            when(productRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> reviewService.getProductSummary(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("builds ratingDistribution with all 5 rating keys initialized to 0")
        void getProductSummary_initializesAllRatingKeys() {
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findAverageRatingByProductId(1L)).thenReturn(null);
            when(reviewRepository.countByProductIdAndApprovedTrue(1L)).thenReturn(0L);
            when(reviewRepository.findRatingDistributionByProductId(1L)).thenReturn(List.of());

            ReviewSummaryResponse result = reviewService.getProductSummary(1L);

            assertThat(result.getRatingDistribution()).containsKeys(1, 2, 3, 4, 5);
            assertThat(result.getRatingDistribution().values()).allMatch(v -> v == 0L);
        }

        @Test
        @DisplayName("populates ratingDistribution from raw query results")
        void getProductSummary_populatesDistribution() {
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findAverageRatingByProductId(1L)).thenReturn(4.8);
            when(reviewRepository.countByProductIdAndApprovedTrue(1L)).thenReturn(5L);
            when(reviewRepository.findRatingDistributionByProductId(1L)).thenReturn(List.of(
                    new Object[]{5, 4L},
                    new Object[]{3, 1L}
            ));

            ReviewSummaryResponse result = reviewService.getProductSummary(1L);

            assertThat(result.getRatingDistribution().get(5)).isEqualTo(4L);
            assertThat(result.getRatingDistribution().get(3)).isEqualTo(1L);
            assertThat(result.getRatingDistribution().get(1)).isEqualTo(0L);
        }

        @Test
        @DisplayName("handles null averageRating when no reviews exist")
        void getProductSummary_nullAverageRating() {
            when(productRepository.existsById(1L)).thenReturn(true);
            when(reviewRepository.findAverageRatingByProductId(1L)).thenReturn(null);
            when(reviewRepository.countByProductIdAndApprovedTrue(1L)).thenReturn(0L);
            when(reviewRepository.findRatingDistributionByProductId(1L)).thenReturn(List.of());

            ReviewSummaryResponse result = reviewService.getProductSummary(1L);

            assertThat(result.getAverageRating()).isNull();
            assertThat(result.getTotalReviews()).isEqualTo(0L);
        }
    }

    // ─── getPendingReviews ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPendingReviews")
    class GetPendingReviews {

        @Test
        @DisplayName("returns list of all unapproved reviews")
        void getPendingReviews_success() {
            when(reviewRepository.findAllByApprovedFalseOrderByCreatedAtDesc())
                    .thenReturn(List.of(review));

            List<ReviewResponse> result = reviewService.getPendingReviews();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isApproved()).isFalse();
        }

        @Test
        @DisplayName("returns empty list when no pending reviews exist")
        void getPendingReviews_empty() {
            when(reviewRepository.findAllByApprovedFalseOrderByCreatedAtDesc())
                    .thenReturn(List.of());

            assertThat(reviewService.getPendingReviews()).isEmpty();
        }

        @Test
        @DisplayName("maps all review fields to response objects")
        void getPendingReviews_mapsFields() {
            when(reviewRepository.findAllByApprovedFalseOrderByCreatedAtDesc())
                    .thenReturn(List.of(review));

            ReviewResponse r = reviewService.getPendingReviews().get(0);

            assertThat(r.getId()).isEqualTo(200L);
            assertThat(r.getTitle()).isEqualTo("Excellent!");
            assertThat(r.getProductName()).isEqualTo("Blue Hoodie");
            assertThat(r.getUserName()).isEqualTo("Jane Doe");
        }

        @Test
        @DisplayName("returns multiple pending reviews in order provided by repository")
        void getPendingReviews_multipleReviews() {
            Review second = Review.builder()
                    .id(201L)
                    .product(product)
                    .user(customer)
                    .rating(3)
                    .title("Okay")
                    .comment("It was alright.")
                    .verified(true)
                    .approved(false)
                    .build();
            when(reviewRepository.findAllByApprovedFalseOrderByCreatedAtDesc())
                    .thenReturn(List.of(review, second));

            List<ReviewResponse> result = reviewService.getPendingReviews();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ReviewResponse::getId).containsExactly(200L, 201L);
        }
    }

    // ─── approveReview ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveReview")
    class ApproveReview {

        @Test
        @DisplayName("sets approved=true and returns updated response")
        void approveReview_success() {
            review.setApproved(false);
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            ReviewResponse result = reviewService.approveReview(200L);

            assertThat(result.isApproved()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when review does not exist")
        void approveReview_notFound() {
            when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.approveReview(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Review");

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("saves the review entity after marking as approved")
        void approveReview_savesAfterApproval() {
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            reviewService.approveReview(200L);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            assertThat(captor.getValue().isApproved()).isTrue();
        }

        @Test
        @DisplayName("is idempotent — approving an already-approved review succeeds")
        void approveReview_alreadyApprovedIsIdempotent() {
            review.setApproved(true);
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() -> reviewService.approveReview(200L));
            assertThat(review.isApproved()).isTrue();
        }

        @Test
        @DisplayName("does not modify other review fields when approving")
        void approveReview_doesNotAlterOtherFields() {
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReviewResponse result = reviewService.approveReview(200L);

            assertThat(result.getRating()).isEqualTo(5);
            assertThat(result.getTitle()).isEqualTo("Excellent!");
            assertThat(result.isVerified()).isTrue();
        }
    }

    // ─── deleteReview ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteReview")
    class DeleteReview {

        @Test
        @DisplayName("owner can delete their own review")
        void deleteReview_ownerCanDelete() {
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));

            assertThatNoException()
                    .isThrownBy(() -> reviewService.deleteReview("jane@example.com", 200L));

            verify(reviewRepository).delete(review);
        }

        @Test
        @DisplayName("admin can delete any review")
        void deleteReview_adminCanDelete() {
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

            assertThatNoException()
                    .isThrownBy(() -> reviewService.deleteReview("admin@example.com", 200L));

            verify(reviewRepository).delete(review);
        }

        @Test
        @DisplayName("throws AccessDeniedException when a different non-admin user tries to delete")
        void deleteReview_otherUserDenied() {
            User otherUser = User.builder()
                    .id(77L).firstName("Other").lastName("Person")
                    .email("other@example.com").password("pw").role(Role.CUSTOMER)
                    .build();

            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

            assertThatThrownBy(() -> reviewService.deleteReview("other@example.com", 200L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not allowed");

            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when review does not exist")
        void deleteReview_reviewNotFound() {
            when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview("jane@example.com", 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Review");

            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user email does not exist")
        void deleteReview_userNotFound() {
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview("ghost@example.com", 200L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("email");

            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("passes exact review entity to repository delete")
        void deleteReview_passesCorrectEntity() {
            when(reviewRepository.findById(200L)).thenReturn(Optional.of(review));
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));

            reviewService.deleteReview("jane@example.com", 200L);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).delete(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(200L);
        }
    }
}
