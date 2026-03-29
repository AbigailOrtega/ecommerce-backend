package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.dto.response.ProductColorResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.dto.response.ProductSizeResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PromotionRepository promotionRepository;
    private final CartItemRepository cartItemRepository;

    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(this::mapToResponse);
    }

    public ProductResponse getProductById(Long id) {
        return mapToResponse(findProductById(id));
    }

    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        return mapToResponse(product);
    }

    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::mapToResponse);
    }

    public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
        return productRepository.search(query, pageable).map(this::mapToResponse);
    }

    public Page<ProductResponse> getFeaturedProducts(Pageable pageable) {
        return productRepository.findByFeaturedTrueAndActiveTrue(pageable).map(this::mapToResponse);
    }

    public List<ProductResponse> getNewArrivals() {
        return productRepository.findTop8ByActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .compareAtPrice(request.compareAtPrice())
                .sku(request.sku())
                .stockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0)
                .imageUrl(request.imageUrl())
                .images(request.images() != null ? request.images() : List.of())
                .featured(request.featured() != null && request.featured())
                .active(request.active() == null || request.active())
                .build();

        if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
            List<Category> categories = categoryRepository.findAllById(request.categoryIds());
            if (categories.size() != request.categoryIds().size()) {
                throw new ResourceNotFoundException("One or more categories not found");
            }
            product.setCategories(categories);
        }

        Product saved = productRepository.save(product);
        applyColors(saved, request);
        return mapToResponse(productRepository.save(saved));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductById(id);

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCompareAtPrice(request.compareAtPrice());
        product.setSku(request.sku());
        if (request.stockQuantity() != null) product.setStockQuantity(request.stockQuantity());
        if (request.imageUrl() != null) product.setImageUrl(request.imageUrl());
        if (request.images() != null) product.setImages(request.images());
        if (request.featured() != null) product.setFeatured(request.featured());
        if (request.active() != null) product.setActive(request.active());

        if (request.categoryIds() != null) {
            List<Category> categories = categoryRepository.findAllById(request.categoryIds());
            if (categories.size() != request.categoryIds().size()) {
                throw new ResourceNotFoundException("One or more categories not found");
            }
            product.setCategories(categories);
        }

        applyColors(product, request);
        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.delete(findProductById(id));
    }

    public Page<ProductResponse> getAllProductsAdmin(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToResponse);
    }

    private void applyColors(Product product, ProductRequest request) {
        if (request.colors() == null) return;

        List<Long> incomingColorIds = request.colors().stream()
                .filter(cr -> cr.id() != null).map(cr -> cr.id()).toList();
        product.getColors().removeIf(existing -> !incomingColorIds.contains(existing.getId()));

        for (var cr : request.colors()) {
            if (cr.name() == null || cr.name().isBlank()) continue;

            ProductColor color = product.getColors().stream()
                    .filter(c -> c.getId() != null && c.getId().equals(cr.id()))
                    .findFirst()
                    .orElse(null);

            if (color == null) {
                color = ProductColor.builder()
                        .product(product)
                        .name(cr.name())
                        .images(cr.images() != null ? new ArrayList<>(cr.images()) : new ArrayList<>())
                        .build();
                product.getColors().add(color);
            } else {
                color.setName(cr.name());
                if (cr.images() != null) {
                    color.getImages().clear();
                    color.getImages().addAll(cr.images());
                }
            }

            if (cr.sizes() != null) {
                List<Long> incomingSizeIds = cr.sizes().stream()
                        .filter(sr -> sr.id() != null).map(sr -> sr.id()).toList();
                color.getSizes().removeIf(existing -> !incomingSizeIds.contains(existing.getId()));

                for (var sr : cr.sizes()) {
                    if (sr.name() == null || sr.name().isBlank()) continue;
                    ProductSize size = color.getSizes().stream()
                            .filter(s -> s.getId() != null && s.getId().equals(sr.id()))
                            .findFirst()
                            .orElse(null);
                    if (size == null) {
                        color.getSizes().add(ProductSize.builder()
                                .color(color)
                                .name(sr.name())
                                .stock(sr.stock() != null ? sr.stock() : 0)
                                .build());
                    } else {
                        size.setName(sr.name());
                        size.setStock(sr.stock() != null ? sr.stock() : 0);
                    }
                }
            }
        }
        // Set product imageUrl to first color's first image if not set
        if ((product.getImageUrl() == null || product.getImageUrl().isBlank())
                && !product.getColors().isEmpty()
                && !product.getColors().get(0).getImages().isEmpty()) {
            product.setImageUrl(product.getColors().get(0).getImages().get(0));
        }
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private ProductResponse mapToResponse(Product product) {
        List<CategoryResponse> categoryResponses = product.getCategories().stream()
                .map(cat -> new CategoryResponse(cat.getId(), cat.getName(), cat.getDescription(), cat.getImageUrl(), cat.getSlug()))
                .toList();

        List<ProductColorResponse> colorResponses = product.getColors().stream()
                .map(c -> new ProductColorResponse(
                        c.getId(),
                        c.getName(),
                        c.getImages(),
                        c.getSizes().stream()
                                .map(s -> new ProductSizeResponse(s.getId(), s.getName(), s.getStock()))
                                .toList()
                ))
                .toList();

        Optional<Promotion> promo = promotionRepository
                .findBestActivePromotion(product.getId(), LocalDate.now());
        BigDecimal discountedPrice = promo.map(p ->
                product.getPrice().multiply(BigDecimal.ONE
                        .subtract(p.getDiscountPercent().divide(BigDecimal.valueOf(100))))
                        .setScale(2, RoundingMode.HALF_UP)
        ).orElse(null);

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getSku(),
                product.getStockQuantity(),
                product.getImageUrl(),
                product.getImages(),
                product.getSlug(),
                product.isFeatured(),
                product.isActive(),
                categoryResponses,
                product.getCreatedAt(),
                colorResponses,
                discountedPrice,
                promo.map(Promotion::getDiscountPercent).orElse(null),
                promo.map(Promotion::getName).orElse(null)
        );
    }
}
