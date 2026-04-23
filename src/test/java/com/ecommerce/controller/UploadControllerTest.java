package com.ecommerce.controller;

import com.ecommerce.config.SecurityConfig;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.service.CloudinaryService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = UploadController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
    }
)
@Import(GlobalExceptionHandler.class)
@DisplayName("UploadController")
class UploadControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CloudinaryService cloudinaryService;

    // ── POST /api/upload/image ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/upload/image")
    class UploadImage {

        @Test
        @DisplayName("returns 200 with the uploaded image URL on success")
        void uploadImage_200() throws Exception {
            when(cloudinaryService.upload(any(), any()))
                    .thenReturn("https://res.cloudinary.com/demo/image/upload/products/test-image.jpg");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test-image.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "fake-image-bytes".getBytes()
            );

            mockMvc.perform(multipart("/api/upload/image").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.url")
                            .value("https://res.cloudinary.com/demo/image/upload/products/test-image.jpg"));
        }

        @Test
        @DisplayName("returns 400 when Cloudinary is not configured")
        void uploadImage_400_cloudinaryNotConfigured() throws Exception {
            when(cloudinaryService.upload(any(), any()))
                    .thenThrow(new BadRequestException("Cloudinary is not configured."));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test-image.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "fake-image-bytes".getBytes()
            );

            mockMvc.perform(multipart("/api/upload/image").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("returns 400 when no file part is included in the request")
        void uploadImage_400_missingFilePart() throws Exception {
            // Sending a multipart request without the required "file" part triggers
            // a MissingServletRequestPartException -> 400 before the controller runs.
            mockMvc.perform(multipart("/api/upload/image"))
                    .andExpect(status().isBadRequest());
        }
    }
}
