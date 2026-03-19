package com.ecommerce.service;

import com.ecommerce.dto.request.ShippingMethodRequest;
import com.ecommerce.dto.response.ShippingMethodResponse;
import com.ecommerce.entity.ShippingMethod;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ShippingMethodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingMethodService")
class ShippingMethodServiceTest {

    @Mock private ShippingMethodRepository repository;

    @InjectMocks private ShippingMethodService shippingMethodService;

    private ShippingMethod activeMethod;
    private ShippingMethod inactiveMethod;

    @BeforeEach
    void setUp() {
        activeMethod = ShippingMethod.builder()
                .id(1L)
                .name("Standard")
                .description("3-5 business days")
                .price(BigDecimal.valueOf(99))
                .estimatedDays(5)
                .active(true)
                .displayOrder(1)
                .build();

        inactiveMethod = ShippingMethod.builder()
                .id(2L)
                .name("Express")
                .description("Next day")
                .price(BigDecimal.valueOf(199))
                .estimatedDays(1)
                .active(false)
                .displayOrder(2)
                .build();
    }

    // ─── getActiveMethods ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getActiveMethods")
    class GetActiveMethods {

        @Test
        @DisplayName("returns only active methods in display order")
        void getActiveMethods_returnsActiveMethods() {
            when(repository.findAllByActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(activeMethod));

            List<ShippingMethodResponse> result = shippingMethodService.getActiveMethods();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Standard");
            assertThat(result.get(0).active()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when no active methods exist")
        void getActiveMethods_emptyWhenNoneActive() {
            when(repository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

            assertThat(shippingMethodService.getActiveMethods()).isEmpty();
        }

        @Test
        @DisplayName("maps all response fields correctly")
        void getActiveMethods_mapsAllFields() {
            when(repository.findAllByActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(activeMethod));

            ShippingMethodResponse response = shippingMethodService.getActiveMethods().get(0);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Standard");
            assertThat(response.description()).isEqualTo("3-5 business days");
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(99));
            assertThat(response.estimatedDays()).isEqualTo(5);
            assertThat(response.displayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns multiple active methods")
        void getActiveMethods_returnsMultiple() {
            ShippingMethod second = ShippingMethod.builder()
                    .id(3L).name("Economy").price(BigDecimal.valueOf(50))
                    .active(true).displayOrder(3).build();
            when(repository.findAllByActiveTrueOrderByDisplayOrderAsc())
                    .thenReturn(List.of(activeMethod, second));

            List<ShippingMethodResponse> result = shippingMethodService.getActiveMethods();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ShippingMethodResponse::name)
                    .containsExactly("Standard", "Economy");
        }

        @Test
        @DisplayName("delegates ordering to repository, not in-memory sorting")
        void getActiveMethods_delegatesOrderingToRepository() {
            when(repository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

            shippingMethodService.getActiveMethods();

            verify(repository).findAllByActiveTrueOrderByDisplayOrderAsc();
            verify(repository, never()).findAll();
        }
    }

    // ─── getAllMethods ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllMethods")
    class GetAllMethods {

        @Test
        @DisplayName("returns all methods including inactive ones")
        void getAllMethods_includesInactive() {
            when(repository.findAll()).thenReturn(List.of(activeMethod, inactiveMethod));

            List<ShippingMethodResponse> result = shippingMethodService.getAllMethods();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ShippingMethodResponse::active)
                    .containsExactlyInAnyOrder(true, false);
        }

        @Test
        @DisplayName("sorts results by displayOrder ascending")
        void getAllMethods_sortsByDisplayOrder() {
            ShippingMethod third = ShippingMethod.builder()
                    .id(3L).name("Economy").price(BigDecimal.valueOf(50))
                    .active(true).displayOrder(0).build();
            when(repository.findAll()).thenReturn(List.of(activeMethod, inactiveMethod, third));

            List<ShippingMethodResponse> result = shippingMethodService.getAllMethods();

            assertThat(result).extracting(ShippingMethodResponse::displayOrder)
                    .isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        @DisplayName("returns empty list when repository is empty")
        void getAllMethods_emptyList() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(shippingMethodService.getAllMethods()).isEmpty();
        }

        @Test
        @DisplayName("maps id and name correctly for each method")
        void getAllMethods_mapsFields() {
            when(repository.findAll()).thenReturn(List.of(activeMethod));

            ShippingMethodResponse response = shippingMethodService.getAllMethods().get(0);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Standard");
        }

        @Test
        @DisplayName("returns single-element list when only one method exists")
        void getAllMethods_singleElement() {
            when(repository.findAll()).thenReturn(List.of(activeMethod));

            List<ShippingMethodResponse> result = shippingMethodService.getAllMethods();

            assertThat(result).hasSize(1);
        }
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("saves a new method and returns its response")
        void create_success() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Overnight", "Delivers next day", BigDecimal.valueOf(249), 1, 0);
            ShippingMethod saved = ShippingMethod.builder()
                    .id(10L).name("Overnight").description("Delivers next day")
                    .price(BigDecimal.valueOf(249)).estimatedDays(1).active(true).displayOrder(0)
                    .build();
            when(repository.save(any(ShippingMethod.class))).thenReturn(saved);

            ShippingMethodResponse response = shippingMethodService.create(request);

            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.name()).isEqualTo("Overnight");
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(249));
        }

        @Test
        @DisplayName("builds entity from request fields before saving")
        void create_buildsEntityFromRequest() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Cargo", "Heavy items", BigDecimal.valueOf(500), 7, 5);
            when(repository.save(any(ShippingMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            shippingMethodService.create(request);

            ArgumentCaptor<ShippingMethod> captor = ArgumentCaptor.forClass(ShippingMethod.class);
            verify(repository).save(captor.capture());
            ShippingMethod entity = captor.getValue();
            assertThat(entity.getName()).isEqualTo("Cargo");
            assertThat(entity.getDescription()).isEqualTo("Heavy items");
            assertThat(entity.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
            assertThat(entity.getEstimatedDays()).isEqualTo(7);
            assertThat(entity.getDisplayOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("allows null description")
        void create_nullDescription() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Free", null, BigDecimal.ZERO, null, 0);
            when(repository.save(any(ShippingMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.create(request);

            assertThat(response.description()).isNull();
        }

        @Test
        @DisplayName("allows null estimatedDays")
        void create_nullEstimatedDays() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Unknown", "TBD", BigDecimal.valueOf(75), null, 0);
            when(repository.save(any(ShippingMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.create(request);

            assertThat(response.estimatedDays()).isNull();
        }

        @Test
        @DisplayName("calls repository.save exactly once")
        void create_savesExactlyOnce() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Test", null, BigDecimal.ONE, null, 0);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            shippingMethodService.create(request);

            verify(repository, times(1)).save(any());
        }
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates all editable fields and returns updated response")
        void update_success() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Premium", "Same day", BigDecimal.valueOf(350), 0, 2);
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));
            when(repository.save(any(ShippingMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.update(1L, request);

            assertThat(response.name()).isEqualTo("Premium");
            assertThat(response.description()).isEqualTo("Same day");
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(350));
            assertThat(response.estimatedDays()).isEqualTo(0);
            assertThat(response.displayOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when method does not exist")
        void update_notFound() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "X", null, BigDecimal.ONE, null, 0);
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingMethodService.update(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ShippingMethod");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("mutates the entity retrieved from the repository")
        void update_mutatesExistingEntity() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Updated Name", "Updated Desc", BigDecimal.valueOf(150), 3, 10);
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            shippingMethodService.update(1L, request);

            ArgumentCaptor<ShippingMethod> captor = ArgumentCaptor.forClass(ShippingMethod.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(1L);
            assertThat(captor.getValue().getName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("preserves active status after update")
        void update_preservesActiveStatus() {
            ShippingMethodRequest request = new ShippingMethodRequest(
                    "Standard v2", null, BigDecimal.valueOf(99), 5, 1);
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.update(1L, request);

            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("does not call save when entity is not found")
        void update_doesNotSaveWhenNotFound() {
            when(repository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingMethodService.update(42L,
                    new ShippingMethodRequest("X", null, BigDecimal.ONE, null, 0)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).save(any());
        }
    }

    // ─── toggle ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("sets inactive when method is currently active")
        void toggle_activeToInactive() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.toggle(1L);

            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("sets active when method is currently inactive")
        void toggle_inactiveToActive() {
            when(repository.findById(2L)).thenReturn(Optional.of(inactiveMethod));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.toggle(2L);

            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when method does not exist")
        void toggle_notFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingMethodService.toggle(999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("saves the entity after toggling")
        void toggle_savesCalled() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            shippingMethodService.toggle(1L);

            verify(repository).save(activeMethod);
        }

        @Test
        @DisplayName("does not change any other field besides active")
        void toggle_doesNotAlterOtherFields() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ShippingMethodResponse response = shippingMethodService.toggle(1L);

            assertThat(response.name()).isEqualTo("Standard");
            assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(99));
            assertThat(response.displayOrder()).isEqualTo(1);
        }
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes the method when it exists")
        void delete_success() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));

            shippingMethodService.delete(1L);

            verify(repository).delete(activeMethod);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when method does not exist")
        void delete_notFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingMethodService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ShippingMethod");

            verify(repository, never()).delete(any());
        }

        @Test
        @DisplayName("passes the correct entity instance to repository.delete")
        void delete_passesCorrectEntity() {
            when(repository.findById(2L)).thenReturn(Optional.of(inactiveMethod));

            shippingMethodService.delete(2L);

            verify(repository).delete(inactiveMethod);
        }

        @Test
        @DisplayName("calls repository.delete exactly once")
        void delete_deletesExactlyOnce() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));

            shippingMethodService.delete(1L);

            verify(repository, times(1)).delete(any());
        }

        @Test
        @DisplayName("never calls repository.save during delete")
        void delete_doesNotSave() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));

            shippingMethodService.delete(1L);

            verify(repository, never()).save(any());
        }
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns method when found")
        void findById_success() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));

            ShippingMethod result = shippingMethodService.findById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Standard");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when id does not exist")
        void findById_notFound() {
            when(repository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingMethodService.findById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ShippingMethod")
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("exception message contains the resource name")
        void findById_exceptionContainsResourceName() {
            when(repository.findById(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingMethodService.findById(7L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ShippingMethod");
        }

        @Test
        @DisplayName("returns inactive method when it exists")
        void findById_returnsInactiveMethod() {
            when(repository.findById(2L)).thenReturn(Optional.of(inactiveMethod));

            ShippingMethod result = shippingMethodService.findById(2L);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("queries repository with the exact id provided")
        void findById_queriesCorrectId() {
            when(repository.findById(1L)).thenReturn(Optional.of(activeMethod));

            shippingMethodService.findById(1L);

            verify(repository).findById(1L);
        }
    }
}
