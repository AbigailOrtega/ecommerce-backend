package com.ecommerce.service;

import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private CategoryService categoryService;

    private Category shoes;
    private Category bags;

    @BeforeEach
    void setUp() {
        shoes = Category.builder()
                .id(1L)
                .name("Shoes")
                .description("All kinds of shoes")
                .imageUrl("https://cdn.example.com/shoes.jpg")
                .slug("shoes")
                .build();

        bags = Category.builder()
                .id(2L)
                .name("Bags")
                .description("Handbags and backpacks")
                .imageUrl("https://cdn.example.com/bags.jpg")
                .slug("bags")
                .build();
    }

    // ─── getAllCategories ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllCategories")
    class GetAllCategories {

        @Test
        @DisplayName("returns a mapped response for every category in the repository")
        void getAllCategories_returnsMappedList() {
            when(categoryRepository.findAll()).thenReturn(List.of(shoes, bags));

            List<CategoryResponse> result = categoryService.getAllCategories();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CategoryResponse::name)
                    .containsExactly("Shoes", "Bags");
        }

        @Test
        @DisplayName("returns empty list when no categories exist")
        void getAllCategories_returnsEmptyListWhenRepositoryIsEmpty() {
            when(categoryRepository.findAll()).thenReturn(List.of());

            assertThat(categoryService.getAllCategories()).isEmpty();
        }

        @Test
        @DisplayName("maps all fields of each category correctly")
        void getAllCategories_mapsAllFieldsCorrectly() {
            when(categoryRepository.findAll()).thenReturn(List.of(shoes));

            CategoryResponse response = categoryService.getAllCategories().get(0);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Shoes");
            assertThat(response.description()).isEqualTo("All kinds of shoes");
            assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/shoes.jpg");
            assertThat(response.slug()).isEqualTo("shoes");
        }

        @Test
        @DisplayName("handles a category with null description and imageUrl gracefully")
        void getAllCategories_handlesNullOptionalFields() {
            Category sparse = Category.builder().id(3L).name("Hats").slug("hats").build();
            when(categoryRepository.findAll()).thenReturn(List.of(sparse));

            CategoryResponse response = categoryService.getAllCategories().get(0);

            assertThat(response.description()).isNull();
            assertThat(response.imageUrl()).isNull();
        }

        @Test
        @DisplayName("returns a single-element list when exactly one category exists")
        void getAllCategories_returnsSingleElement() {
            when(categoryRepository.findAll()).thenReturn(List.of(shoes));

            assertThat(categoryService.getAllCategories()).hasSize(1);
        }
    }

    // ─── getCategoryById ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCategoryById")
    class GetCategoryById {

        @Test
        @DisplayName("returns the mapped response when category exists")
        void getCategoryById_success() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));

            CategoryResponse result = categoryService.getCategoryById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Shoes");
            assertThat(result.slug()).isEqualTo("shoes");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when id does not exist")
        void getCategoryById_notFound() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getCategoryById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category")
                    .hasMessageContaining("id")
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("maps imageUrl and description correctly from found category")
        void getCategoryById_mapsImageUrlAndDescription() {
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(bags));

            CategoryResponse result = categoryService.getCategoryById(2L);

            assertThat(result.description()).isEqualTo("Handbags and backpacks");
            assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/bags.jpg");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for id zero")
        void getCategoryById_idZeroNotFound() {
            when(categoryRepository.findById(0L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getCategoryById(0L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("does not call repository more than once for a single request")
        void getCategoryById_callsRepositoryExactlyOnce() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));

            categoryService.getCategoryById(1L);

            verify(categoryRepository).findById(1L);
        }
    }

    // ─── createCategory ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCategory")
    class CreateCategory {

        @Test
        @DisplayName("saves category and returns mapped response")
        void createCategory_success() {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(5L);
                c.setSlug("sneakers");
                return c;
            });

            CategoryResponse result = categoryService.createCategory("Sneakers", "All sneakers", "img.jpg");

            assertThat(result.id()).isEqualTo(5L);
            assertThat(result.name()).isEqualTo("Sneakers");
            assertThat(result.description()).isEqualTo("All sneakers");
            assertThat(result.imageUrl()).isEqualTo("img.jpg");
        }

        @Test
        @DisplayName("passes correct name, description, and imageUrl to the repository")
        void createCategory_passesCorrectFieldsToRepository() {
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            when(categoryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            categoryService.createCategory("Jackets", "Winter jackets", "jackets.jpg");

            Category saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Jackets");
            assertThat(saved.getDescription()).isEqualTo("Winter jackets");
            assertThat(saved.getImageUrl()).isEqualTo("jackets.jpg");
        }

        @Test
        @DisplayName("creates category with null description and null imageUrl when not provided")
        void createCategory_withNullOptionalFields() {
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            when(categoryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            categoryService.createCategory("Caps", null, null);

            Category saved = captor.getValue();
            assertThat(saved.getDescription()).isNull();
            assertThat(saved.getImageUrl()).isNull();
        }

        @Test
        @DisplayName("returns the id assigned by the repository after save")
        void createCategory_returnsIdFromRepository() {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(42L);
                return c;
            });

            CategoryResponse result = categoryService.createCategory("Boots", null, null);

            assertThat(result.id()).isEqualTo(42L);
        }

        @Test
        @DisplayName("calls repository save exactly once per invocation")
        void createCategory_savesExactlyOnce() {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            categoryService.createCategory("Hats", "Summer hats", null);

            verify(categoryRepository).save(any(Category.class));
        }
    }

    // ─── updateCategory ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategory {

        @Test
        @DisplayName("updates name, description, and imageUrl when all values are provided")
        void updateCategory_updatesAllFields() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse result = categoryService.updateCategory(
                    1L, "Running Shoes", "Updated description", "new-image.jpg");

            assertThat(result.name()).isEqualTo("Running Shoes");
            assertThat(result.description()).isEqualTo("Updated description");
            assertThat(result.imageUrl()).isEqualTo("new-image.jpg");
        }

        @Test
        @DisplayName("does not overwrite description when null is passed")
        void updateCategory_doesNotOverwriteDescriptionWhenNull() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse result = categoryService.updateCategory(1L, "Shoes", null, null);

            assertThat(result.description()).isEqualTo("All kinds of shoes");
        }

        @Test
        @DisplayName("does not overwrite imageUrl when null is passed")
        void updateCategory_doesNotOverwriteImageUrlWhenNull() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse result = categoryService.updateCategory(1L, "Shoes", null, null);

            assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/shoes.jpg");
        }

        @Test
        @DisplayName("always updates the name even when description and imageUrl are null")
        void updateCategory_alwaysUpdatesName() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse result = categoryService.updateCategory(1L, "Sports Shoes", null, null);

            assertThat(result.name()).isEqualTo("Sports Shoes");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when category id does not exist")
        void updateCategory_notFound() {
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    categoryService.updateCategory(999L, "X", null, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category")
                    .hasMessageContaining("999");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("persists changes by calling save with the mutated entity")
        void updateCategory_callsSaveWithMutatedEntity() {
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(bags));
            when(categoryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            categoryService.updateCategory(2L, "Backpacks", "School backpacks", "bp.jpg");

            Category saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Backpacks");
            assertThat(saved.getDescription()).isEqualTo("School backpacks");
            assertThat(saved.getImageUrl()).isEqualTo("bp.jpg");
        }
    }

    // ─── deleteCategory ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategory {

        @Test
        @DisplayName("deletes the category when it exists")
        void deleteCategory_success() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));

            assertThatNoException().isThrownBy(() -> categoryService.deleteCategory(1L));

            verify(categoryRepository).delete(shoes);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when category does not exist")
        void deleteCategory_notFound() {
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category")
                    .hasMessageContaining("999");

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deletes the exact entity instance returned by findById")
        void deleteCategory_deletesCorrectEntity() {
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(bags));

            categoryService.deleteCategory(2L);

            verify(categoryRepository).delete(bags);
        }

        @Test
        @DisplayName("does not call delete when the category is not found")
        void deleteCategory_noDeleteOnMissingId() {
            when(categoryRepository.findById(55L)).thenReturn(Optional.empty());

            try {
                categoryService.deleteCategory(55L);
            } catch (ResourceNotFoundException ignored) {
                // expected
            }

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("calls findById before attempting deletion")
        void deleteCategory_lookupHappensBeforeDelete() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(shoes));

            categoryService.deleteCategory(1L);

            verify(categoryRepository).findById(1L);
        }
    }
}
