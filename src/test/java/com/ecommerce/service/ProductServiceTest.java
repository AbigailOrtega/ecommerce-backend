package com.ecommerce.service;

import com.ecommerce.dto.request.ProductColorRequest;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSizeRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
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
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private com.ecommerce.repository.CartItemRepository cartItemRepository;

    @InjectMocks private ProductService productService;

    private Product sampleProduct;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Blue Hoodie")
                .description("Warm and cozy")
                .price(BigDecimal.valueOf(59.99))
                .compareAtPrice(BigDecimal.valueOf(79.99))
                .sku("SKU-001")
                .stockQuantity(25)
                .imageUrl("http://example.com/hoodie.jpg")
                .images(new ArrayList<>(List.of("http://example.com/hoodie.jpg")))
                .slug("blue-hoodie")
                .featured(false)
                .active(true)
                .categories(new ArrayList<>())
                .colors(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        pageable = PageRequest.of(0, 10);
    }

    // helper — avoids repeating promotionRepository stub in every test
    private void noActivePromotion() {
        when(promotionRepository.findBestActivePromotion(anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
    }

    // ─── getAllProducts ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllProducts")
    class GetAllProducts {

        @Test
        @DisplayName("returns page of active products mapped to response")
        void getAllProducts_returnsActivePage() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.findByActiveTrue(pageable)).thenReturn(page);
            noActivePromotion();

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Blue Hoodie");
        }

        @Test
        @DisplayName("returns empty page when no active products exist")
        void getAllProducts_emptyPage() {
            when(productRepository.findByActiveTrue(pageable)).thenReturn(Page.empty());

            Page<ProductResponse> result = productService.getAllProducts(pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("maps price and sku correctly")
        void getAllProducts_mapsFieldsCorrectly() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.findByActiveTrue(pageable)).thenReturn(page);
            noActivePromotion();

            ProductResponse response = productService.getAllProducts(pageable).getContent().get(0);

            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(59.99));
            assertThat(response.sku()).isEqualTo("SKU-001");
            assertThat(response.slug()).isEqualTo("blue-hoodie");
        }

        @Test
        @DisplayName("includes active promotion discount in response when promotion exists")
        void getAllProducts_withActivePromotion() {
            Promotion promo = Promotion.builder()
                    .id(10L)
                    .name("Summer Sale")
                    .discountPercent(BigDecimal.valueOf(20))
                    .build();
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.findByActiveTrue(pageable)).thenReturn(page);
            when(promotionRepository.findBestActivePromotion(eq(1L), any(LocalDate.class)))
                    .thenReturn(Optional.of(promo));

            ProductResponse response = productService.getAllProducts(pageable).getContent().get(0);

            // 59.99 * (1 - 0.20) = 47.99
            assertThat(response.discountedPrice()).isEqualByComparingTo(BigDecimal.valueOf(47.99));
            assertThat(response.activePromotionName()).isEqualTo("Summer Sale");
            assertThat(response.activePromotionDiscount()).isEqualByComparingTo(BigDecimal.valueOf(20));
        }

        @Test
        @DisplayName("sets discountedPrice to null when no active promotion")
        void getAllProducts_noPromotion_discountedPriceNull() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.findByActiveTrue(pageable)).thenReturn(page);
            noActivePromotion();

            ProductResponse response = productService.getAllProducts(pageable).getContent().get(0);

            assertThat(response.discountedPrice()).isNull();
            assertThat(response.activePromotionName()).isNull();
        }
    }

    // ─── getProductById ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductById")
    class GetProductById {

        @Test
        @DisplayName("returns product response for existing id")
        void getProductById_success() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            ProductResponse result = productService.getProductById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Blue Hoodie");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void getProductById_notFound() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("maps featured flag correctly")
        void getProductById_mapsFeaturedFlag() {
            sampleProduct.setFeatured(true);
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            assertThat(productService.getProductById(1L).featured()).isTrue();
        }

        @Test
        @DisplayName("maps active flag correctly")
        void getProductById_mapsActiveFlag() {
            sampleProduct.setActive(false);
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            assertThat(productService.getProductById(1L).active()).isFalse();
        }

        @Test
        @DisplayName("maps categories correctly when product has categories")
        void getProductById_mapsCategories() {
            Category cat = Category.builder()
                    .id(5L)
                    .name("Hoodies")
                    .slug("hoodies")
                    .products(new ArrayList<>())
                    .build();
            sampleProduct.setCategories(new ArrayList<>(List.of(cat)));
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            ProductResponse result = productService.getProductById(1L);

            assertThat(result.categories()).hasSize(1);
            assertThat(result.categories().get(0).name()).isEqualTo("Hoodies");
        }
    }

    // ─── getProductBySlug ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductBySlug")
    class GetProductBySlug {

        @Test
        @DisplayName("returns product response for existing slug")
        void getProductBySlug_success() {
            when(productRepository.findBySlug("blue-hoodie")).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            ProductResponse result = productService.getProductBySlug("blue-hoodie");

            assertThat(result.slug()).isEqualTo("blue-hoodie");
            assertThat(result.name()).isEqualTo("Blue Hoodie");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when slug is not found")
        void getProductBySlug_notFound() {
            when(productRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductBySlug("unknown-slug"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("slug");
        }

        @Test
        @DisplayName("passes exact slug string to repository")
        void getProductBySlug_passesSlugToRepo() {
            when(productRepository.findBySlug("blue-hoodie")).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            productService.getProductBySlug("blue-hoodie");

            verify(productRepository).findBySlug("blue-hoodie");
        }

        @Test
        @DisplayName("still resolves even when slug has numeric suffix")
        void getProductBySlug_numericSuffix() {
            sampleProduct.setSlug("blue-hoodie-2");
            when(productRepository.findBySlug("blue-hoodie-2")).thenReturn(Optional.of(sampleProduct));
            noActivePromotion();

            ProductResponse result = productService.getProductBySlug("blue-hoodie-2");

            assertThat(result.slug()).isEqualTo("blue-hoodie-2");
        }
    }

    // ─── getProductsByCategory ────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductsByCategory")
    class GetProductsByCategory {

        @Test
        @DisplayName("returns page of products for a given category")
        void getProductsByCategory_success() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.findByCategoryId(5L, pageable)).thenReturn(page);
            noActivePromotion();

            Page<ProductResponse> result = productService.getProductsByCategory(5L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Blue Hoodie");
        }

        @Test
        @DisplayName("returns empty page when category has no products")
        void getProductsByCategory_empty() {
            when(productRepository.findByCategoryId(99L, pageable)).thenReturn(Page.empty());

            Page<ProductResponse> result = productService.getProductsByCategory(99L, pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("passes category id and pageable correctly to repository")
        void getProductsByCategory_passesArgsToRepo() {
            when(productRepository.findByCategoryId(5L, pageable)).thenReturn(Page.empty());

            productService.getProductsByCategory(5L, pageable);

            verify(productRepository).findByCategoryId(5L, pageable);
        }
    }

    // ─── searchProducts ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchProducts")
    class SearchProducts {

        @Test
        @DisplayName("returns matching products for a search query")
        void searchProducts_success() {
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.search("hoodie", pageable)).thenReturn(page);
            noActivePromotion();

            Page<ProductResponse> result = productService.searchProducts("hoodie", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Blue Hoodie");
        }

        @Test
        @DisplayName("returns empty page when no products match query")
        void searchProducts_noMatches() {
            when(productRepository.search("nonexistent", pageable)).thenReturn(Page.empty());

            Page<ProductResponse> result = productService.searchProducts("nonexistent", pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("passes query string and pageable to repository unchanged")
        void searchProducts_passesQueryToRepo() {
            when(productRepository.search("jacket", pageable)).thenReturn(Page.empty());

            productService.searchProducts("jacket", pageable);

            verify(productRepository).search("jacket", pageable);
        }
    }

    // ─── getFeaturedProducts ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeaturedProducts")
    class GetFeaturedProducts {

        @Test
        @DisplayName("returns page of featured and active products")
        void getFeaturedProducts_success() {
            sampleProduct.setFeatured(true);
            Page<Product> page = new PageImpl<>(List.of(sampleProduct));
            when(productRepository.findByFeaturedTrueAndActiveTrue(pageable)).thenReturn(page);
            noActivePromotion();

            Page<ProductResponse> result = productService.getFeaturedProducts(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).featured()).isTrue();
        }

        @Test
        @DisplayName("returns empty page when no featured products exist")
        void getFeaturedProducts_empty() {
            when(productRepository.findByFeaturedTrueAndActiveTrue(pageable)).thenReturn(Page.empty());

            assertThat(productService.getFeaturedProducts(pageable).getContent()).isEmpty();
        }
    }

    // ─── getNewArrivals ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getNewArrivals")
    class GetNewArrivals {

        @Test
        @DisplayName("returns up to 8 newest active products")
        void getNewArrivals_returnsUpToEight() {
            when(productRepository.findTop8ByActiveTrueOrderByCreatedAtDesc())
                    .thenReturn(List.of(sampleProduct));
            noActivePromotion();

            List<ProductResponse> result = productService.getNewArrivals();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Blue Hoodie");
        }

        @Test
        @DisplayName("returns empty list when no active products exist")
        void getNewArrivals_empty() {
            when(productRepository.findTop8ByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of());

            assertThat(productService.getNewArrivals()).isEmpty();
        }

        @Test
        @DisplayName("maps all returned products to response objects")
        void getNewArrivals_mapsAllProducts() {
            Product second = Product.builder()
                    .id(2L).name("Red Tee").price(BigDecimal.valueOf(29.99))
                    .categories(new ArrayList<>()).colors(new ArrayList<>()).active(true)
                    .build();
            when(productRepository.findTop8ByActiveTrueOrderByCreatedAtDesc())
                    .thenReturn(List.of(sampleProduct, second));
            noActivePromotion();

            List<ProductResponse> result = productService.getNewArrivals();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProductResponse::id).containsExactly(1L, 2L);
        }
    }

    // ─── createProduct ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        private ProductRequest minimalRequest() {
            return new ProductRequest(
                    "Blue Hoodie", "Warm", BigDecimal.valueOf(59.99),
                    null, "SKU-001", 10, null, null, null, null, null, null);
        }

        @Test
        @DisplayName("creates product with required fields and returns response")
        void createProduct_success() {
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                p.setCategories(new ArrayList<>());
                p.setColors(new ArrayList<>());
                return p;
            });
            noActivePromotion();

            ProductResponse result = productService.createProduct(minimalRequest());

            assertThat(result.name()).isEqualTo("Blue Hoodie");
            assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(59.99));
        }

        @Test
        @DisplayName("defaults stockQuantity to 0 when request provides null")
        void createProduct_defaultsStockToZero() {
            ProductRequest request = new ProductRequest(
                    "Cap", null, BigDecimal.ONE,
                    null, null, null, null, null, null, null, null, null);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                p.setCategories(new ArrayList<>());
                p.setColors(new ArrayList<>());
                return p;
            });
            noActivePromotion();

            ProductResponse result = productService.createProduct(request);

            assertThat(result.stockQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("defaults active to true when request provides null")
        void createProduct_defaultsActiveTrue() {
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            when(productRepository.save(captor.capture())).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                p.setCategories(new ArrayList<>());
                p.setColors(new ArrayList<>());
                return p;
            });
            noActivePromotion();

            productService.createProduct(minimalRequest());

            // The first save call receives the product before colors; second save returns final
            assertThat(captor.getAllValues().get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("resolves and attaches categories when categoryIds are provided")
        void createProduct_withCategories() {
            Category cat = Category.builder().id(3L).name("Outerwear")
                    .slug("outerwear").products(new ArrayList<>()).build();
            ProductRequest request = new ProductRequest(
                    "Parka", null, BigDecimal.valueOf(120), null, null,
                    5, null, null, List.of(3L), null, null, null);

            when(categoryRepository.findAllById(List.of(3L))).thenReturn(List.of(cat));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(2L);
                if (p.getColors() == null) p.setColors(new ArrayList<>());
                return p;
            });
            noActivePromotion();

            ProductResponse result = productService.createProduct(request);

            assertThat(result.categories()).hasSize(1);
            assertThat(result.categories().get(0).name()).isEqualTo("Outerwear");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when one or more category ids are missing")
        void createProduct_categoryNotFound() {
            ProductRequest request = new ProductRequest(
                    "Parka", null, BigDecimal.valueOf(120), null, null,
                    5, null, null, List.of(3L, 4L), null, null, null);

            // only 1 returned but 2 requested
            when(categoryRepository.findAllById(anyList())).thenReturn(List.of(
                    Category.builder().id(3L).name("Outerwear").products(new ArrayList<>()).build()));

            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("categories");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("applies colors and sets imageUrl from first color image when imageUrl not provided")
        void createProduct_setsImageUrlFromFirstColorImage() {
            ProductColorRequest colorReq = new ProductColorRequest(
                    "Red", List.of("http://img.com/red.jpg"), List.of());
            ProductRequest request = new ProductRequest(
                    "Tee", null, BigDecimal.valueOf(20), null, null,
                    10, null, null, null, null, null, List.of(colorReq));

            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(3L);
                if (p.getCategories() == null) p.setCategories(new ArrayList<>());
                return p;
            });
            noActivePromotion();

            ProductResponse result = productService.createProduct(request);

            assertThat(result.imageUrl()).isEqualTo("http://img.com/red.jpg");
        }

        @Test
        @DisplayName("skips color entries with blank name")
        void createProduct_skipsBlankColorNames() {
            ProductColorRequest blankColor = new ProductColorRequest("  ", List.of(), null);
            ProductRequest request = new ProductRequest(
                    "Tee", null, BigDecimal.valueOf(20), null, null,
                    10, "http://img.com/tee.jpg", null, null, null, null, List.of(blankColor));

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            when(productRepository.save(captor.capture())).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(4L);
                if (p.getCategories() == null) p.setCategories(new ArrayList<>());
                return p;
            });
            noActivePromotion();

            productService.createProduct(request);

            // After both saves, the colors list should be empty because blank name is skipped
            Product lastSaved = captor.getValue();
            assertThat(lastSaved.getColors()).isEmpty();
        }
    }

    // ─── updateProduct ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProduct")
    class UpdateProduct {

        private ProductRequest updateRequest(String name, BigDecimal price) {
            return new ProductRequest(name, "Updated desc", price,
                    null, "SKU-UPDATED", 20, null, null, null, null, true, null);
        }

        @Test
        @DisplayName("updates product fields and returns response")
        void updateProduct_success() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            noActivePromotion();

            ProductResponse result = productService.updateProduct(1L, updateRequest("New Name", BigDecimal.valueOf(99.99)));

            assertThat(result.name()).isEqualTo("New Name");
            assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void updateProduct_notFound() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(999L, updateRequest("X", BigDecimal.ONE)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when one or more category ids are missing")
        void updateProduct_categoryNotFound() {
            ProductRequest request = new ProductRequest(
                    "Parka", null, BigDecimal.valueOf(120), null, null,
                    5, null, null, List.of(10L, 11L), null, null, null);

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(categoryRepository.findAllById(anyList())).thenReturn(List.of(
                    Category.builder().id(10L).name("A").products(new ArrayList<>()).build()));

            assertThatThrownBy(() -> productService.updateProduct(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips stockQuantity update when request provides null")
        void updateProduct_nullStockQuantityIgnored() {
            sampleProduct.setStockQuantity(25);
            ProductRequest request = new ProductRequest(
                    "Blue Hoodie", "desc", BigDecimal.valueOf(59.99),
                    null, "SKU-001", null, null, null, null, null, null, null);

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            when(productRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
            noActivePromotion();

            productService.updateProduct(1L, request);

            assertThat(captor.getValue().getStockQuantity()).isEqualTo(25);
        }

        @Test
        @DisplayName("updates categories when new categoryIds are provided")
        void updateProduct_updatesCategories() {
            Category cat = Category.builder().id(7L).name("New Cat")
                    .slug("new-cat").products(new ArrayList<>()).build();
            ProductRequest request = new ProductRequest(
                    "Blue Hoodie", "desc", BigDecimal.valueOf(59.99),
                    null, null, null, null, null, List.of(7L), null, null, null);

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(categoryRepository.findAllById(List.of(7L))).thenReturn(List.of(cat));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            noActivePromotion();

            ProductResponse result = productService.updateProduct(1L, request);

            assertThat(result.categories()).hasSize(1);
            assertThat(result.categories().get(0).id()).isEqualTo(7L);
        }
    }

    // ─── deleteProduct ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProduct {

        @Test
        @DisplayName("deletes product when it exists")
        void deleteProduct_success() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

            productService.deleteProduct(1L);

            verify(productRepository).delete(sampleProduct);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void deleteProduct_notFound() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("passes exact product entity to repository delete")
        void deleteProduct_passesCorrectEntityToDelete() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

            productService.deleteProduct(1L);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).delete(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(1L);
        }
    }

    // ─── getAllProductsAdmin ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllProductsAdmin")
    class GetAllProductsAdmin {

        @Test
        @DisplayName("returns all products regardless of active status")
        void getAllProductsAdmin_success() {
            Product inactive = Product.builder()
                    .id(2L).name("Inactive Product").price(BigDecimal.TEN)
                    .active(false).categories(new ArrayList<>()).colors(new ArrayList<>())
                    .build();
            Page<Product> page = new PageImpl<>(List.of(sampleProduct, inactive));
            when(productRepository.findAll(pageable)).thenReturn(page);
            noActivePromotion();

            Page<ProductResponse> result = productService.getAllProductsAdmin(pageable);

            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("returns empty page when no products exist at all")
        void getAllProductsAdmin_empty() {
            when(productRepository.findAll(pageable)).thenReturn(Page.empty());

            assertThat(productService.getAllProductsAdmin(pageable).getContent()).isEmpty();
        }

        @Test
        @DisplayName("includes inactive products that would be excluded from public listing")
        void getAllProductsAdmin_includesInactiveProducts() {
            Product inactive = Product.builder()
                    .id(2L).name("Hidden Product").price(BigDecimal.TEN)
                    .active(false).categories(new ArrayList<>()).colors(new ArrayList<>())
                    .build();
            when(productRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(inactive)));
            noActivePromotion();

            Page<ProductResponse> result = productService.getAllProductsAdmin(pageable);

            assertThat(result.getContent().get(0).active()).isFalse();
        }
    }
}
