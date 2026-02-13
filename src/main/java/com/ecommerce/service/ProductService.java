package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

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

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            product.setCategory(category);
        }

        return mapToResponse(productRepository.save(product));
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

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            product.setCategory(category);
        }

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductById(id);
        productRepository.delete(product);
    }

    public Page<ProductResponse> getAllProductsAdmin(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToResponse);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private ProductResponse mapToResponse(Product product) {
        CategoryResponse categoryResponse = null;
        if (product.getCategory() != null) {
            Category cat = product.getCategory();
            categoryResponse = new CategoryResponse(cat.getId(), cat.getName(), cat.getDescription(), cat.getImageUrl(), cat.getSlug());
        }

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
                categoryResponse,
                product.getCreatedAt()
        );
    }
}
