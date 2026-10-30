package com.ecommerce.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashSet;
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

    /** Every column that stores an image URL: alias, FROM clause and the selected URL expression. */
    private record ImageColumn(String alias, String from, String url) {}

    private static final List<ImageColumn> IMAGE_COLUMNS = List.of(
            new ImageColumn("p", "Product p", "p.imageUrl"),
            new ImageColumn("p", "Product p join p.images i", "i"),
            new ImageColumn("c", "ProductColor c join c.images i", "i"),
            new ImageColumn("c", "Category c", "c.imageUrl"),
            new ImageColumn("b", "PromoBanner b", "b.imageUrl"),
            new ImageColumn("s", "StoreImage s", "s.url"),
            new ImageColumn("s", "StoreInfo s", "s.logoUrl"));

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
        return IMAGE_COLUMNS.stream().anyMatch(col -> entityManager
                .createQuery("select count(" + col.alias() + ") from " + col.from() + " where " + col.url() + " = :url", Long.class)
                .setParameter("url", url).getSingleResult() > 0);
    }

    /** Every image URL used anywhere in the store. */
    public Set<String> referencedUrls() {
        Set<String> urls = new HashSet<>();
        for (ImageColumn col : IMAGE_COLUMNS) {
            entityManager.createQuery("select " + col.url() + " from " + col.from(), String.class)
                    .getResultList().stream().filter(Objects::nonNull).forEach(urls::add);
        }
        return urls;
    }
}
