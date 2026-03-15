package com.ecommerce.service;

import com.ecommerce.dto.request.PromotionRequest;
import com.ecommerce.dto.response.PromotionResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Promotion;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ProductRepository productRepository;

    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        List<Product> products = productRepository.findAllById(request.productIds());
        if (products.size() != request.productIds().size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }
        Promotion promotion = Promotion.builder()
                .name(request.name())
                .discountPercent(request.discountPercent())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .active(true)
                .products(new HashSet<>(products))
                .build();
        return mapToResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        Promotion promotion = findById(id);
        List<Product> products = productRepository.findAllById(request.productIds());
        if (products.size() != request.productIds().size()) {
            throw new ResourceNotFoundException("One or more products not found");
        }
        promotion.setName(request.name());
        promotion.setDiscountPercent(request.discountPercent());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setProducts(new HashSet<>(products));
        return mapToResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public void deletePromotion(Long id) {
        promotionRepository.delete(findById(id));
    }

    @Transactional
    public PromotionResponse togglePromotion(Long id) {
        Promotion promotion = findById(id);
        promotion.setActive(!promotion.isActive());
        return mapToResponse(promotionRepository.save(promotion));
    }

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", id));
    }

    private PromotionResponse mapToResponse(Promotion promotion) {
        List<PromotionResponse.PromotionProductSummary> productSummaries = promotion.getProducts().stream()
                .map(p -> new PromotionResponse.PromotionProductSummary(p.getId(), p.getName(), p.getPrice()))
                .toList();
        return new PromotionResponse(
                promotion.getId(),
                promotion.getName(),
                promotion.getDiscountPercent(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.isActive(),
                productSummaries
        );
    }
}
