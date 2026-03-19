package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.ReviewRequest;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.dto.response.ReviewSummaryResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = ReviewController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("ReviewController")
class ReviewControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ReviewService reviewService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "customer@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@test.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private ReviewResponse stubReview(Long id) {
        return ReviewResponse.builder()
                .id(id)
                .productId(10L)
                .productName("Test T-Shirt")
                .userId(1L)
                .userName("Test User")
                .rating(5)
                .title("Great product")
                .comment("Very comfortable.")
                .verified(true)
                .approved(true)
                .createdAt(LocalDateTime.of(2025, 1, 15, 12, 0))
                .build();
    }

    private ReviewSummaryResponse stubSummary() {
        return ReviewSummaryResponse.builder()
                .averageRating(4.5)
                .totalReviews(20L)
                .ratingDistribution(Map.of(1, 1L, 2, 1L, 3, 2L, 4, 6L, 5, 10L))
                .build();
    }

    // ── GET /api/products/{productId}/reviews ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/{productId}/reviews")
    class GetProductReviews {

        @Test
        @DisplayName("returns 200 with paged review list")
        void getReviews_200() throws Exception {
            var page = new PageImpl<>(List.of(stubReview(1L), stubReview(2L)),
                    PageRequest.of(0, 10), 2);
            when(reviewService.getProductReviews(eq(10L), any())).thenReturn(page);

            mockMvc.perform(get("/api/products/10/reviews")
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.content[0].rating").value(5))
                    .andExpect(jsonPath("$.data.content[0].title").value("Great product"))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("returns 200 with empty page when product has no reviews")
        void getReviews_200_empty() throws Exception {
            var emptyPage = new PageImpl<ReviewResponse>(List.of(), PageRequest.of(0, 10), 0);
            when(reviewService.getProductReviews(eq(10L), any())).thenReturn(emptyPage);

            mockMvc.perform(get("/api/products/10/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("returns 404 when product does not exist")
        void getReviews_404() throws Exception {
            when(reviewService.getProductReviews(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("Product", "id", 999L));

            mockMvc.perform(get("/api/products/999/reviews"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("uses default pagination when no params supplied")
        void getReviews_defaultPagination() throws Exception {
            var page = new PageImpl<ReviewResponse>(List.of(), PageRequest.of(0, 10), 0);
            when(reviewService.getProductReviews(eq(10L), any())).thenReturn(page);

            mockMvc.perform(get("/api/products/10/reviews"))
                    .andExpect(status().isOk());

            verify(reviewService).getProductReviews(eq(10L), any());
        }
    }

    // ── GET /api/products/{productId}/reviews/summary ─────────────────────────

    @Nested
    @DisplayName("GET /api/products/{productId}/reviews/summary")
    class GetProductSummary {

        @Test
        @DisplayName("returns 200 with summary data")
        void getSummary_200() throws Exception {
            when(reviewService.getProductSummary(10L)).thenReturn(stubSummary());

            mockMvc.perform(get("/api/products/10/reviews/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.averageRating").value(4.5))
                    .andExpect(jsonPath("$.data.totalReviews").value(20))
                    .andExpect(jsonPath("$.data.ratingDistribution").isMap());
        }

        @Test
        @DisplayName("returns 404 when product does not exist")
        void getSummary_404() throws Exception {
            when(reviewService.getProductSummary(999L))
                    .thenThrow(new ResourceNotFoundException("Product", "id", 999L));

            mockMvc.perform(get("/api/products/999/reviews/summary"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── POST /api/products/{productId}/reviews ────────────────────────────────

    @Nested
    @DisplayName("POST /api/products/{productId}/reviews")
    class CreateReview {

        @Test
        @DisplayName("returns 200 with created review for authenticated user")
        void createReview_200() throws Exception {
            ReviewRequest req = new ReviewRequest(5, "Great product", "Very comfortable.");
            when(reviewService.createReview(eq("customer@test.com"), eq(10L), any(ReviewRequest.class)))
                    .thenReturn(stubReview(1L));

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.rating").value(5))
                    .andExpect(jsonPath("$.data.title").value("Great product"))
                    .andExpect(jsonPath("$.data.verified").value(true));
        }

        @Test
        @DisplayName("returns 400 when rating is out of range (0)")
        void createReview_400_ratingTooLow() throws Exception {
            String body = "{\"rating\":0,\"title\":\"Bad\",\"comment\":\"Terrible\"}";

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when rating is out of range (6)")
        void createReview_400_ratingTooHigh() throws Exception {
            String body = "{\"rating\":6,\"title\":\"Wow\",\"comment\":\"Amazing\"}";

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when title is blank")
        void createReview_400_blankTitle() throws Exception {
            String body = "{\"rating\":4,\"title\":\"\",\"comment\":\"Good\"}";

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when comment is blank")
        void createReview_400_blankComment() throws Exception {
            String body = "{\"rating\":4,\"title\":\"Good\",\"comment\":\"\"}";

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when review body is missing required fields")
        void createReview_400_missingFields() throws Exception {
            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 when product does not exist")
        void createReview_404_productNotFound() throws Exception {
            ReviewRequest req = new ReviewRequest(4, "Good", "Pretty good.");
            when(reviewService.createReview(anyString(), eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("Product", "id", 999L));

            mockMvc.perform(post("/api/products/999/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 403 when user has not purchased the product")
        void createReview_403_notPurchased() throws Exception {
            ReviewRequest req = new ReviewRequest(3, "Meh", "Never bought this.");
            when(reviewService.createReview(anyString(), eq(10L), any()))
                    .thenThrow(new AccessDeniedException(
                            "You can only review products you have purchased and received."));

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when user already reviewed the product")
        void createReview_400_alreadyReviewed() throws Exception {
            ReviewRequest req = new ReviewRequest(5, "Still great", "Buying again.");
            when(reviewService.createReview(anyString(), eq(10L), any()))
                    .thenThrow(new BadRequestException("You have already reviewed this product."));

            mockMvc.perform(post("/api/products/10/reviews")
                    .principal(customerAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── DELETE /api/reviews/{reviewId} ────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/reviews/{reviewId}")
    class DeleteReview {

        @Test
        @DisplayName("returns 200 when owner deletes their own review")
        void deleteReview_200_owner() throws Exception {
            doNothing().when(reviewService).deleteReview("customer@test.com", 1L);

            mockMvc.perform(delete("/api/reviews/1")
                    .principal(customerAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(reviewService).deleteReview("customer@test.com", 1L);
        }

        @Test
        @DisplayName("returns 200 when admin deletes any review")
        void deleteReview_200_admin() throws Exception {
            doNothing().when(reviewService).deleteReview("admin@test.com", 1L);

            mockMvc.perform(delete("/api/reviews/1")
                    .principal(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(reviewService).deleteReview("admin@test.com", 1L);
        }

        @Test
        @DisplayName("returns 404 when review does not exist")
        void deleteReview_404() throws Exception {
            doThrow(new ResourceNotFoundException("Review", "id", 999L))
                    .when(reviewService).deleteReview(anyString(), eq(999L));

            mockMvc.perform(delete("/api/reviews/999")
                    .principal(customerAuth()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 403 when a different user tries to delete the review")
        void deleteReview_403_notOwner() throws Exception {
            doThrow(new AccessDeniedException("You are not allowed to delete this review."))
                    .when(reviewService).deleteReview(anyString(), eq(1L));

            mockMvc.perform(delete("/api/reviews/1")
                    .principal(customerAuth()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
