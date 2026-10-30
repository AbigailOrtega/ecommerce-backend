package com.ecommerce.service;

import com.ecommerce.service.CloudinaryService.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryCleanupJob")
class CloudinaryCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final String BASE = "https://res.cloudinary.com/shop/image/upload/v1/";

    @Mock CloudinaryService cloudinary;
    @Mock ImageCleanupService imageCleanup;
    CloudinaryCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new CloudinaryCleanupJob(cloudinary, imageCleanup, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(cloudinary.isConfigured()).thenReturn(true);
        lenient().when(cloudinary.publicIdOf(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return url.startsWith(BASE) ? Optional.of(url.substring(BASE.length(), url.lastIndexOf('.'))) : Optional.empty();
        });
    }

    private static StoredImage image(String id, Duration age) {
        return new StoredImage(id, NOW.minus(age));
    }

    @Test
    @DisplayName("deletes images older than 7 days that nothing uses; keeps used and recent ones")
    void deletesOldUnusedImages() throws Exception {
        when(imageCleanup.referencedUrls()).thenReturn(Set.of(BASE + "products/used.webp", "https://other.com/x.jpg"));
        when(cloudinary.listImages("products")).thenReturn(List.of(
                image("products/used", Duration.ofDays(30)),
                image("products/orphan", Duration.ofDays(30)),
                image("products/recent", Duration.ofDays(2))));
        when(cloudinary.listImages("banners")).thenReturn(List.of(image("banners/old", Duration.ofDays(8))));
        when(cloudinary.listImages("uploads")).thenReturn(List.of());

        assertThat(job.run()).isEqualTo(2);

        verify(cloudinary).deleteByPublicIds(List.of("products/orphan", "banners/old"));
    }

    @Test
    @DisplayName("only looks at the store's folders")
    void onlyStoreFolders() throws Exception {
        when(imageCleanup.referencedUrls()).thenReturn(Set.of(BASE + "products/used.webp"));
        when(cloudinary.listImages(anyString())).thenReturn(List.of());

        job.run();

        verify(cloudinary).listImages("products");
        verify(cloudinary).listImages("banners");
        verify(cloudinary).listImages("uploads");
        verify(cloudinary, never()).deleteByPublicIds(any());
    }

    @Test
    @DisplayName("never deletes anything when the store references no Cloudinary image")
    void skipsWhenNothingReferenced() throws Exception {
        when(imageCleanup.referencedUrls()).thenReturn(Set.of("https://other.com/x.jpg"));

        assertThat(job.run()).isZero();

        verify(cloudinary, never()).listImages(anyString());
        verify(cloudinary, never()).deleteByPublicIds(any());
    }

    @Test
    @DisplayName("deletes nothing if listing a folder fails")
    void listingFails() throws Exception {
        when(imageCleanup.referencedUrls()).thenReturn(Set.of(BASE + "products/used.webp"));
        when(cloudinary.listImages("products")).thenReturn(List.of(image("products/orphan", Duration.ofDays(30))));
        when(cloudinary.listImages("banners")).thenThrow(new RuntimeException("rate limited"));

        assertThat(job.run()).isZero();

        verify(cloudinary, never()).deleteByPublicIds(any());
    }

    @Test
    @DisplayName("does nothing when Cloudinary is not configured")
    void notConfigured() {
        when(cloudinary.isConfigured()).thenReturn(false);

        assertThat(job.run()).isZero();

        verifyNoInteractions(imageCleanup);
    }
}
