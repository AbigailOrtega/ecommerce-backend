package com.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deletes images in the store's Cloudinary folders that nothing in the store uses, such as
 * uploads abandoned in a closed browser tab or images orphaned before automatic cleanup existed.
 * Runs once a day (and shortly after each start, since Render may put the service to sleep).
 */
@Slf4j
@Component
public class CloudinaryCleanupJob {

    /** Only the folders CloudinaryService.upload() writes to; anything else in the account is left alone. */
    static final List<String> FOLDERS = List.of("products", "banners", "uploads");

    private final CloudinaryService cloudinaryService;
    private final ImageCleanupService imageCleanupService;
    private final Clock clock;

    @Value("${app.cloudinary.cleanup.enabled:true}")
    private boolean enabled = true;

    /** Younger images may belong to a form that is still being filled in. */
    @Value("${app.cloudinary.cleanup.min-age:P7D}")
    private Duration minAge = Duration.ofDays(7);

    @Autowired
    public CloudinaryCleanupJob(CloudinaryService cloudinaryService, ImageCleanupService imageCleanupService) {
        this(cloudinaryService, imageCleanupService, Clock.systemUTC());
    }

    CloudinaryCleanupJob(CloudinaryService cloudinaryService, ImageCleanupService imageCleanupService, Clock clock) {
        this.cloudinaryService = cloudinaryService;
        this.imageCleanupService = imageCleanupService;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.cloudinary.cleanup.initial-delay:PT10M}",
               fixedDelayString = "${app.cloudinary.cleanup.interval:P1D}")
    public void scheduledRun() {
        run();
    }

    /** @return how many images were deleted. */
    public int run() {
        if (!enabled || !cloudinaryService.isConfigured()) return 0;
        try {
            Set<String> inUse = imageCleanupService.referencedUrls().stream()
                    .map(cloudinaryService::publicIdOf)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet());
            if (inUse.isEmpty()) {
                // never wipe the folders because of an empty or unreadable database
                log.warn("Cloudinary cleanup skipped: the store references no Cloudinary images");
                return 0;
            }

            Instant cutoff = clock.instant().minus(minAge);
            List<String> unused = new ArrayList<>();
            for (String folder : FOLDERS) {
                cloudinaryService.listImages(folder).stream()
                        .filter(img -> img.createdAt().isBefore(cutoff))
                        .map(CloudinaryService.StoredImage::publicId)
                        .filter(id -> !inUse.contains(id))
                        .forEach(unused::add);
            }

            if (!unused.isEmpty()) {
                cloudinaryService.deleteByPublicIds(unused);
                log.info("Cloudinary cleanup deleted {} unused images: {}", unused.size(), unused);
            } else {
                log.info("Cloudinary cleanup: no unused images");
            }
            return unused.size();
        } catch (Exception e) {
            log.error("Cloudinary cleanup failed: {}", e.getMessage(), e);
            return 0;
        }
    }
}
