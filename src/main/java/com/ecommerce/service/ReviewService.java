package com.ecommerce.service;

import com.ecommerce.dto.request.ReviewRequest;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.dto.response.ReviewSummaryResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ReviewResponse createReview(String email, Long productId, ReviewRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        boolean hasPurchased = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .flatMap(o -> o.getItems().stream())
                .anyMatch(item -> item.getProduct() != null && item.getProduct().getId().equals(productId));

        if (!hasPurchased) {
            throw new AccessDeniedException("You can only review products you have purchased and received.");
        }

        if (reviewRepository.existsByProductIdAndUserId(productId, user.getId())) {
            throw new BadRequestException("You have already reviewed this product.");
        }

        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating(request.rating())
                .title(request.title())
                .comment(request.comment())
                .verified(true)
                .build();

        Review saved = reviewRepository.save(review);
        return mapToResponse(saved);
    }

    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        return reviewRepository.findByProductIdAndApprovedTrue(productId, pageable).map(this::mapToResponse);
    }

    public ReviewSummaryResponse getProductSummary(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        long total = reviewRepository.countByProductIdAndApprovedTrue(productId);
        List<Object[]> rawDist = reviewRepository.findRatingDistributionByProductId(productId);

        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0L);
        for (Object[] row : rawDist) {
            distribution.put((Integer) row[0], (Long) row[1]);
        }

        return ReviewSummaryResponse.builder()
                .averageRating(avg)
                .totalReviews(total)
                .ratingDistribution(distribution)
                .build();
    }

    public List<ReviewResponse> getPendingReviews() {
        return reviewRepository.findAllByApprovedFalseOrderByCreatedAtDesc()
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public ReviewResponse approveReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        review.setApproved(true);
        return mapToResponse(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(String email, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        boolean isOwner = review.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not allowed to delete this review.");
        }
        reviewRepository.delete(review);
    }

    private ReviewResponse mapToResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .userId(review.getUser().getId())
                .userName(review.getUser().getFirstName() + " " + review.getUser().getLastName())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .verified(review.isVerified())
                .approved(review.isApproved())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
