package com.ecommerce.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Removes images from Cloudinary once nothing in the store uses them. The same URL can be shared
 * (e.g. a product's main image is also its first color image), so every candidate is checked
 * against all image columns before deleting, and only after the change has been committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCleanupService {

    private static final List<String> REFERENCE_QUERIES = List.of(
            "select count(p) from Product p where p.imageUrl = :url",
            "select count(p) from Product p join p.images i where i = :url",
            "select count(c) from ProductColor c join c.images i where i = :url",
            "select count(c) from Category c where c.imageUrl = :url",
            "select count(b) from PromoBanner b where b.imageUrl = :url",
            "select count(s) from StoreImage s where s.url = :url",
            "select count(s) from StoreInfo s where s.logoUrl = :url");

    private final CloudinaryService cloudinaryService;

    @PersistenceContext
    private EntityManager entityManager;

    /** Images that were in {@code before} but are no longer in {@code after}. */
    public void deleteRemoved(Collection<String> before, Collection<String> after) {
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);
        deleteUnused(removed);
    }

    /** Deletes each image that no record references any more, after the current transaction commits. */
    public void deleteUnused(Collection<String> urls) {
        List<String> candidates = urls.stream().filter(Objects::nonNull).filter(u -> !u.isBlank()).distinct().toList();
        if (candidates.isEmpty()) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    candidates.forEach(ImageCleanupService.this::deleteIfUnused);
                }
            });
        } else {
            candidates.forEach(this::deleteIfUnused);
        }
    }

    /** @return true if the image was unused and a delete was sent to Cloudinary. */
    public boolean deleteIfUnused(String url) {
        try {
            if (isReferenced(url)) return false;
            cloudinaryService.delete(url);
            return true;
        } catch (Exception e) {
            log.error("Could not clean up image {}: {}", url, e.getMessage());
            return false;
        }
    }

    boolean isReferenced(String url) {
        return REFERENCE_QUERIES.stream().anyMatch(q ->
                entityManager.createQuery(q, Long.class).setParameter("url", url).getSingleResult() > 0);
    }
}
