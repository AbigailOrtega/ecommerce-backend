package com.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.ecommerce.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryService")
class CloudinaryServiceTest {

    @InjectMocks private CloudinaryService cloudinaryService;

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;
    @Mock private MultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        // Inject the mocked Cloudinary instance directly, bypassing @PostConstruct
        ReflectionTestUtils.setField(cloudinaryService, "cloudinary", cloudinary);
        ReflectionTestUtils.setField(cloudinaryService, "cloudName", "test-cloud");
        ReflectionTestUtils.setField(cloudinaryService, "apiKey",    "test-api-key");
        ReflectionTestUtils.setField(cloudinaryService, "apiSecret", "test-api-secret");
        ReflectionTestUtils.setField(cloudinaryService, "productMaxWidth",  800);
        ReflectionTestUtils.setField(cloudinaryService, "productMaxHeight", 800);
        ReflectionTestUtils.setField(cloudinaryService, "bannerMaxWidth",  1920);
        ReflectionTestUtils.setField(cloudinaryService, "bannerMaxHeight",  600);
    }

    // ─── isConfigured ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isConfigured")
    class IsConfigured {

        @Test
        @DisplayName("returns true when cloudinary instance is present")
        void isConfigured_true() {
            assertThat(cloudinaryService.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("returns false when cloudinary instance is null")
        void isConfigured_false() {
            ReflectionTestUtils.setField(cloudinaryService, "cloudinary", null);
            assertThat(cloudinaryService.isConfigured()).isFalse();
        }
    }

    // ─── upload ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("returns secure_url when upload succeeds")
        void upload_success() throws IOException {
            byte[] fileBytes = "image-data".getBytes();
            when(multipartFile.getBytes()).thenReturn(fileBytes);
            when(cloudinary.uploader()).thenReturn(uploader);
            when(uploader.upload(eq(fileBytes), any(Map.class)))
                    .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/test-cloud/image/upload/products/img.jpg"));

            String result = cloudinaryService.upload(multipartFile, "product");

            assertThat(result).isEqualTo("https://res.cloudinary.com/test-cloud/image/upload/products/img.jpg");
        }

        @Test
        @DisplayName("sends upload options with folder=products and resource_type=image")
        void upload_sendsCorrectOptions() throws IOException {
            byte[] fileBytes = "img".getBytes();
            when(multipartFile.getBytes()).thenReturn(fileBytes);
            when(cloudinary.uploader()).thenReturn(uploader);

            when(uploader.upload(eq(fileBytes), argThat((Map<?,?> opts) ->
                    "products".equals(opts.get("folder")) &&
                    "image".equals(opts.get("resource_type")))))
                    .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/test/img.jpg"));

            String url = cloudinaryService.upload(multipartFile, "product");

            assertThat(url).contains("cloudinary.com");
        }

        @Test
        @DisplayName("throws BadRequestException when cloudinary is not configured")
        void upload_notConfigured() {
            ReflectionTestUtils.setField(cloudinaryService, "cloudinary", null);

            assertThatThrownBy(() -> cloudinaryService.upload(multipartFile, "product"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cloudinary is not configured.");

            verifyNoInteractions(multipartFile);
        }

        @Test
        @DisplayName("wraps IOException in RuntimeException when upload fails")
        void upload_ioException() throws IOException {
            when(multipartFile.getBytes()).thenReturn(new byte[]{1, 2, 3});
            when(cloudinary.uploader()).thenReturn(uploader);
            when(uploader.upload(any(byte[].class), any(Map.class)))
                    .thenThrow(new IOException("network error"));

            assertThatThrownBy(() -> cloudinaryService.upload(multipartFile, "product"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload image");
        }

        @Test
        @DisplayName("wraps IOException from getBytes() in RuntimeException")
        void upload_getBytesThrows() throws IOException {
            when(multipartFile.getBytes()).thenThrow(new IOException("read error"));

            assertThatThrownBy(() -> cloudinaryService.upload(multipartFile, "product"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload image");
        }

        @Test
        @DisplayName("returns null secure_url when Cloudinary response omits the key")
        void upload_missingSecureUrl() throws IOException {
            when(multipartFile.getBytes()).thenReturn(new byte[]{9});
            when(cloudinary.uploader()).thenReturn(uploader);
            when(uploader.upload(any(byte[].class), any(Map.class)))
                    .thenReturn(Map.of("public_id", "products/img"));  // no secure_url key

            String result = cloudinaryService.upload(multipartFile, "product");

            assertThat(result).isNull();
        }
    }
}
