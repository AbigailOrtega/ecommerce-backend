package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.CategoryService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = CategoryController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@DisplayName("CategoryController")
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CategoryService categoryService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CategoryResponse stubCategory(Long id, String name, String slug) {
        return new CategoryResponse(id, name, "A test category", "https://img.example.com/cat.jpg", slug);
    }

    // ─── GET /api/categories ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/categories")
    class GetAllCategories {

        @Test
        @DisplayName("returns 200 with list of categories")
        void getAll_200() throws Exception {
            when(categoryService.getAllCategories()).thenReturn(List.of(
                    stubCategory(1L, "Tops", "tops"),
                    stubCategory(2L, "Bottoms", "bottoms")));

            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("Tops"))
                    .andExpect(jsonPath("$.data[0].slug").value("tops"))
                    .andExpect(jsonPath("$.data[1].name").value("Bottoms"));
        }

        @Test
        @DisplayName("returns 200 with empty list when no categories exist")
        void getAll_200_empty() throws Exception {
            when(categoryService.getAllCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ─── GET /api/categories/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/categories/{id}")
    class GetCategoryById {

        @Test
        @DisplayName("returns 200 with category when it exists")
        void getById_200() throws Exception {
            when(categoryService.getCategoryById(1L)).thenReturn(stubCategory(1L, "Tops", "tops"));

            mockMvc.perform(get("/api/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Tops"))
                    .andExpect(jsonPath("$.data.description").value("A test category"))
                    .andExpect(jsonPath("$.data.imageUrl").value("https://img.example.com/cat.jpg"))
                    .andExpect(jsonPath("$.data.slug").value("tops"));
        }

        @Test
        @DisplayName("returns 404 when category does not exist")
        void getById_404() throws Exception {
            when(categoryService.getCategoryById(99L))
                    .thenThrow(new ResourceNotFoundException("Category", "id", 99L));

            mockMvc.perform(get("/api/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ─── POST /api/categories ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/categories")
    class CreateCategory {

        @Test
        @DisplayName("returns 201 with created category on valid request")
        void create_201() throws Exception {
            when(categoryService.createCategory("Tops", "Top clothing", "https://img.example.com/tops.jpg"))
                    .thenReturn(stubCategory(1L, "Tops", "tops"));

            mockMvc.perform(post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "name", "Tops",
                            "description", "Top clothing",
                            "imageUrl", "https://img.example.com/tops.jpg"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Category created"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Tops"));
        }

        @Test
        @DisplayName("returns 201 when only name is provided (description and imageUrl are optional)")
        void create_201_nameOnly() throws Exception {
            when(categoryService.createCategory(eq("Tops"), isNull(), isNull()))
                    .thenReturn(stubCategory(1L, "Tops", "tops"));

            mockMvc.perform(post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Tops\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("Tops"));
        }

        @Test
        @DisplayName("calls categoryService.createCategory with correct parameters")
        void create_callsService() throws Exception {
            when(categoryService.createCategory(anyString(), anyString(), anyString()))
                    .thenReturn(stubCategory(1L, "Tops", "tops"));

            mockMvc.perform(post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "name", "Tops",
                            "description", "Top clothing",
                            "imageUrl", "https://img.example.com/tops.jpg"))))
                    .andExpect(status().isCreated());

            verify(categoryService).createCategory("Tops", "Top clothing", "https://img.example.com/tops.jpg");
        }
    }

    // ─── PUT /api/categories/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/categories/{id}")
    class UpdateCategory {

        @Test
        @DisplayName("returns 200 with updated category on valid request")
        void update_200() throws Exception {
            when(categoryService.updateCategory(eq(1L), eq("Tops Updated"), anyString(), anyString()))
                    .thenReturn(stubCategory(1L, "Tops Updated", "tops-updated"));

            mockMvc.perform(put("/api/categories/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "name", "Tops Updated",
                            "description", "Updated description",
                            "imageUrl", "https://img.example.com/updated.jpg"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Category updated"))
                    .andExpect(jsonPath("$.data.name").value("Tops Updated"));
        }

        @Test
        @DisplayName("returns 404 when category to update does not exist")
        void update_404() throws Exception {
            when(categoryService.updateCategory(eq(99L), any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Category", "id", 99L));

            mockMvc.perform(put("/api/categories/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Ghost\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("calls categoryService.updateCategory with id and request fields")
        void update_callsService() throws Exception {
            when(categoryService.updateCategory(eq(1L), anyString(), any(), any()))
                    .thenReturn(stubCategory(1L, "Tops", "tops"));

            mockMvc.perform(put("/api/categories/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Tops\", \"description\": \"desc\"}"))
                    .andExpect(status().isOk());

            verify(categoryService).updateCategory(eq(1L), eq("Tops"), eq("desc"), isNull());
        }
    }

    // ─── DELETE /api/categories/{id} ──────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/categories/{id}")
    class DeleteCategory {

        @Test
        @DisplayName("returns 200 with success message when category is deleted")
        void delete_200() throws Exception {
            doNothing().when(categoryService).deleteCategory(1L);

            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Category deleted"));

            verify(categoryService).deleteCategory(1L);
        }

        @Test
        @DisplayName("returns 404 when category to delete does not exist")
        void delete_404() throws Exception {
            doThrow(new ResourceNotFoundException("Category", "id", 99L))
                    .when(categoryService).deleteCategory(99L);

            mockMvc.perform(delete("/api/categories/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
