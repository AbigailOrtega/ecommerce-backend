package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.ProductService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = ProductController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProductService productService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductResponse stubProduct(Long id, String name, String slug) {
        return new ProductResponse(
                id,
                name,
                "A test description",
                new BigDecimal("29.99"),
                null,
                "SKU-" + id,
                10,
                "https://img.example.com/product.jpg",
                List.of(),
                slug,
                false,
                true,
                List.of(new CategoryResponse(1L, "Tops", null, null, "tops")),
                LocalDateTime.of(2024, 1, 15, 10, 0),
                List.of(),
                null,
                null,
                null
        );
    }

    private ProductRequest validProductRequest() {
        return new ProductRequest(
                "Test T-Shirt",
                "A comfortable test t-shirt",
                new BigDecimal("29.99"),
                null,
                "SKU-001",
                10,
                "https://img.example.com/product.jpg",
                List.of(),
                List.of(1L),
                false,
                true,
                List.of()
        );
    }

    // ─── GET /api/products ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products")
    class GetAllProducts {

        @Test
        @DisplayName("returns 200 with a page of products")
        void getAll_200() throws Exception {
            List<ProductResponse> items = List.of(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));
            Page<ProductResponse> page = new PageImpl<>(items, PageRequest.of(0, 12), items.size());
            when(productService.getAllProducts(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].name").value("Test T-Shirt"))
                    .andExpect(jsonPath("$.data.content[0].slug").value("test-t-shirt"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 200 with an empty page when no products exist")
        void getAll_200_empty() throws Exception {
            when(productService.getAllProducts(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 12), 0));

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }

    // ─── GET /api/products/{id} ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductById {

        @Test
        @DisplayName("returns 200 with the product when it exists")
        void getById_200() throws Exception {
            when(productService.getProductById(1L))
                    .thenReturn(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));

            mockMvc.perform(get("/api/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test T-Shirt"))
                    .andExpect(jsonPath("$.data.price").value(29.99));
        }

        @Test
        @DisplayName("returns 404 when product does not exist")
        void getById_404() throws Exception {
            when(productService.getProductById(99L))
                    .thenThrow(new ResourceNotFoundException("Product", "id", 99L));

            mockMvc.perform(get("/api/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ─── GET /api/products/slug/{slug} ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/slug/{slug}")
    class GetProductBySlug {

        @Test
        @DisplayName("returns 200 with the product when slug exists")
        void getBySlug_200() throws Exception {
            when(productService.getProductBySlug("test-t-shirt"))
                    .thenReturn(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));

            mockMvc.perform(get("/api/products/slug/test-t-shirt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.slug").value("test-t-shirt"));
        }

        @Test
        @DisplayName("returns 404 when slug does not exist")
        void getBySlug_404() throws Exception {
            when(productService.getProductBySlug("nonexistent-slug"))
                    .thenThrow(new ResourceNotFoundException("Product", "slug", "nonexistent-slug"));

            mockMvc.perform(get("/api/products/slug/nonexistent-slug"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ─── GET /api/products/category/{categoryId} ──────────────────────────────

    @Nested
    @DisplayName("GET /api/products/category/{categoryId}")
    class GetProductsByCategory {

        @Test
        @DisplayName("returns 200 with products for the given category")
        void getByCategory_200() throws Exception {
            List<ProductResponse> items2 = List.of(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));
            Page<ProductResponse> page = new PageImpl<>(items2, PageRequest.of(0, 12), items2.size());
            when(productService.getProductsByCategory(eq(1L), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/products/category/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].categories[0].id").value(1));
        }

        @Test
        @DisplayName("returns 200 with empty page when category has no products")
        void getByCategory_200_empty() throws Exception {
            when(productService.getProductsByCategory(eq(5L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 12), 0));

            mockMvc.perform(get("/api/products/category/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }

    // ─── GET /api/products/search ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/search")
    class SearchProducts {

        @Test
        @DisplayName("returns 200 with matching products")
        void search_200() throws Exception {
            List<ProductResponse> items3 = List.of(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));
            Page<ProductResponse> page = new PageImpl<>(items3, PageRequest.of(0, 12), items3.size());
            when(productService.searchProducts(eq("shirt"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/products/search").param("q", "shirt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].name").value("Test T-Shirt"));
        }

        @Test
        @DisplayName("returns 200 with empty page when no products match")
        void search_200_noResults() throws Exception {
            when(productService.searchProducts(eq("xyz"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 12), 0));

            mockMvc.perform(get("/api/products/search").param("q", "xyz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }

    // ─── GET /api/products/featured ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/featured")
    class GetFeaturedProducts {

        @Test
        @DisplayName("returns 200 with featured products page")
        void getFeatured_200() throws Exception {
            ProductResponse featured = new ProductResponse(
                    2L, "Featured Item", "desc", new BigDecimal("49.99"),
                    null, "SKU-002", 5, null, List.of(), "featured-item",
                    true, true, List.of(), LocalDateTime.now(), List.of(), null, null, null);
            List<ProductResponse> featuredList = List.of(featured);
            when(productService.getFeaturedProducts(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(featuredList, PageRequest.of(0, 8), featuredList.size()));

            mockMvc.perform(get("/api/products/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].featured").value(true));
        }
    }

    // ─── GET /api/products/new-arrivals ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/new-arrivals")
    class GetNewArrivals {

        @Test
        @DisplayName("returns 200 with list of new arrivals")
        void getNewArrivals_200() throws Exception {
            when(productService.getNewArrivals())
                    .thenReturn(List.of(
                            stubProduct(1L, "Test T-Shirt", "test-t-shirt"),
                            stubProduct(2L, "Test Jeans", "test-jeans")));

            mockMvc.perform(get("/api/products/new-arrivals"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("Test T-Shirt"));
        }

        @Test
        @DisplayName("returns 200 with empty list when no products exist")
        void getNewArrivals_200_empty() throws Exception {
            when(productService.getNewArrivals()).thenReturn(List.of());

            mockMvc.perform(get("/api/products/new-arrivals"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── POST /api/products ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/products")
    class CreateProduct {

        @Test
        @DisplayName("returns 201 with created product on valid request")
        void create_201() throws Exception {
            ProductRequest req = validProductRequest();
            when(productService.createProduct(any())).thenReturn(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));

            mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product created"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Test T-Shirt"));
        }

        @Test
        @DisplayName("returns 400 when name is blank")
        void create_400_missingName() throws Exception {
            mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"price\": 29.99}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when price is missing")
        void create_400_missingPrice() throws Exception {
            mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Test\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when price is negative")
        void create_400_negativePrice() throws Exception {
            mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Test\", \"price\": -5.00}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("calls productService.createProduct with the request body")
        void create_callsService() throws Exception {
            ProductRequest req = validProductRequest();
            when(productService.createProduct(any())).thenReturn(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));

            mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(productService).createProduct(any(ProductRequest.class));
        }
    }

    // ─── PUT /api/products/{id} ───────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class UpdateProduct {

        @Test
        @DisplayName("returns 200 with updated product on valid request")
        void update_200() throws Exception {
            ProductRequest req = validProductRequest();
            when(productService.updateProduct(eq(1L), any()))
                    .thenReturn(stubProduct(1L, "Test T-Shirt", "test-t-shirt"));

            mockMvc.perform(put("/api/products/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product updated"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("returns 404 when product to update does not exist")
        void update_404() throws Exception {
            ProductRequest req = validProductRequest();
            when(productService.updateProduct(eq(99L), any()))
                    .thenThrow(new ResourceNotFoundException("Product", "id", 99L));

            mockMvc.perform(put("/api/products/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when request body is invalid")
        void update_400_validation() throws Exception {
            mockMvc.perform(put("/api/products/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"price\": -1.00}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── DELETE /api/products/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("returns 200 with success message when product is deleted")
        void delete_200() throws Exception {
            doNothing().when(productService).deleteProduct(1L);

            mockMvc.perform(delete("/api/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product deleted"));

            verify(productService).deleteProduct(1L);
        }

        @Test
        @DisplayName("returns 404 when product to delete does not exist")
        void delete_404() throws Exception {
            doThrow(new ResourceNotFoundException("Product", "id", 99L))
                    .when(productService).deleteProduct(99L);

            mockMvc.perform(delete("/api/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
