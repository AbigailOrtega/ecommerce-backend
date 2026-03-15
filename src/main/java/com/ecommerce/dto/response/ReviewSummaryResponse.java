package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponse {
    private Double averageRating;
    private long totalReviews;
    private Map<Integer, Long> ratingDistribution;
}
