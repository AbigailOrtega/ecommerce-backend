package com.ecommerce.controller;

import com.ecommerce.dto.request.ReviewRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.dto.response.ReviewSummaryResponse;
import com.ecommerce.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReviewResponse> reviews = reviewService.getProductReviews(
                productId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    @GetMapping("/api/products/{productId}/reviews/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getProductSummary(@PathVariable Long productId) {
        ReviewSummaryResponse summary = reviewService.getProductSummary(productId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        ReviewResponse review = reviewService.createReview(authentication.getName(), productId, request);
        return ResponseEntity.ok(ApiResponse.success(review));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long reviewId,
            Authentication authentication) {
        reviewService.deleteReview(authentication.getName(), reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }
}
