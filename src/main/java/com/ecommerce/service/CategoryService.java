package com.ecommerce.service;

import com.ecommerce.util.Slugs;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ImageCleanupService imageCleanupService;

    /** The store's categories: the visible ones. */
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().filter(Category::isVisible).map(this::mapToResponse).toList();
    }

    /** Every category, hidden ones included (the admin's list). */
    public List<CategoryResponse> getAllCategoriesForAdmin() {
        return categoryRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public CategoryResponse setVisible(Long id, boolean visible) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        category.setVisible(visible);
        return mapToResponse(categoryRepository.save(category));
    }

    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse createCategory(String name, String description, String imageUrl) {
        Category category = Category.builder()
                .name(name)
                .slug(Slugs.unique(name, categoryRepository::existsBySlug))
                .description(description)
                .imageUrl(imageUrl)
                .build();
        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, String name, String description, String imageUrl) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        String previousImage = category.getImageUrl();
        category.setName(name);
        if (description != null) category.setDescription(description);
        if (imageUrl != null) category.setImageUrl(imageUrl);
        CategoryResponse response = mapToResponse(categoryRepository.save(category));
        imageCleanupService.deleteRemoved(Collections.singletonList(previousImage), Collections.singletonList(category.getImageUrl()));
        return response;
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        categoryRepository.delete(category);
        imageCleanupService.deleteUnused(Collections.singletonList(category.getImageUrl()));
    }

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl(),
                category.getSlug(),
                category.isVisible()
        );
    }
}
