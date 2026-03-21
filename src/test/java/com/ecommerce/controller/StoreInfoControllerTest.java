package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.dto.request.StoreInfoRequest;
import com.ecommerce.dto.response.StoreInfoResponse;
import com.ecommerce.entity.StoreImage;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.StoreInfoService;
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
    value = StoreInfoController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@DisplayName("StoreInfoController")
class StoreInfoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean StoreInfoService storeInfoService;

    private StoreInfoResponse stubResponse(boolean withImages) {
        List<StoreImage> images = withImages
                ? List.of(StoreImage.builder().id(1L).url("https://img.jpg").displayOrder(1).active(true).build())
                : List.of();
        return new StoreInfoResponse("Mi Tienda", "Descripción", "Misión", "Visión", "+52 55 1234", null, null, images, null, null);
    }

    // ─── GET /api/store-info ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/store-info")
    class GetPublic {

        @Test
        @DisplayName("returns 200 with store info and images")
        void getPublic_200() throws Exception {
            when(storeInfoService.getPublic()).thenReturn(stubResponse(true));

            mockMvc.perform(get("/api/store-info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("Mi Tienda"))
                    .andExpect(jsonPath("$.data.aboutText").value("Descripción"))
                    .andExpect(jsonPath("$.data.mission").value("Misión"))
                    .andExpect(jsonPath("$.data.vision").value("Visión"))
                    .andExpect(jsonPath("$.data.phone").value("+52 55 1234"))
                    .andExpect(jsonPath("$.data.images").isArray())
                    .andExpect(jsonPath("$.data.images[0].url").value("https://img.jpg"));
        }

        @Test
        @DisplayName("returns 200 with empty images array when no images")
        void getPublic_200_noImages() throws Exception {
            when(storeInfoService.getPublic()).thenReturn(stubResponse(false));

            mockMvc.perform(get("/api/store-info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.images").isEmpty());
        }
    }

    // ─── PUT /api/admin/store-info ────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/store-info")
    class Update {

        @Test
        @DisplayName("returns 200 with updated store info")
        void update_200() throws Exception {
            StoreInfoRequest req = new StoreInfoRequest("Nuevo", "Nueva desc", null, null, null, null, null, null, null);
            when(storeInfoService.update(any())).thenReturn(stubResponse(false));

            mockMvc.perform(put("/api/admin/store-info")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("Mi Tienda"));
        }

        @Test
        @DisplayName("calls service.update with request body")
        void update_callsService() throws Exception {
            when(storeInfoService.update(any())).thenReturn(stubResponse(false));

            mockMvc.perform(put("/api/admin/store-info")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Test\"}"))
                    .andExpect(status().isOk());

            verify(storeInfoService).update(any(StoreInfoRequest.class));
        }
    }

    // ─── POST /api/admin/store-info/images ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/store-info/images")
    class AddImage {

        @Test
        @DisplayName("returns 200 with created image")
        void addImage_200() throws Exception {
            StoreImage savedImage = StoreImage.builder().id(5L).url("https://new.img").displayOrder(1).active(true).build();
            when(storeInfoService.addImage(anyString())).thenReturn(savedImage);

            mockMvc.perform(post("/api/admin/store-info/images")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("url", "https://new.img"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(5))
                    .andExpect(jsonPath("$.data.url").value("https://new.img"))
                    .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        @DisplayName("calls service.addImage with url from request body")
        void addImage_callsServiceWithUrl() throws Exception {
            StoreImage img = StoreImage.builder().id(1L).url("https://test.img").displayOrder(1).active(true).build();
            when(storeInfoService.addImage("https://test.img")).thenReturn(img);

            mockMvc.perform(post("/api/admin/store-info/images")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://test.img\"}"))
                    .andExpect(status().isOk());

            verify(storeInfoService).addImage("https://test.img");
        }
    }

    // ─── DELETE /api/admin/store-info/images/{id} ─────────────────────────────

    @Nested
    @DisplayName("DELETE /api/admin/store-info/images/{id}")
    class DeleteImage {

        @Test
        @DisplayName("returns 200 and calls deleteImage with path variable id")
        void deleteImage_200() throws Exception {
            doNothing().when(storeInfoService).deleteImage(3L);

            mockMvc.perform(delete("/api/admin/store-info/images/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(storeInfoService).deleteImage(3L);
        }
    }

    // ─── PUT /api/admin/store-info/images/reorder ─────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/store-info/images/reorder")
    class ReorderImages {

        @Test
        @DisplayName("returns 200 and calls reorderImages with ids list")
        void reorderImages_200() throws Exception {
            doNothing().when(storeInfoService).reorderImages(anyList());

            mockMvc.perform(put("/api/admin/store-info/images/reorder")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[3, 1, 2]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(storeInfoService).reorderImages(List.of(3L, 1L, 2L));
        }
    }
}
