package com.ecommerce.service;

import com.ecommerce.dto.request.PromoBannerRequest;
import com.ecommerce.dto.response.PromoBannerResponse;
import com.ecommerce.entity.PromoBanner;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.PromoBannerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
@DisplayName("PromoBannerService")
class PromoBannerServiceTest {

    @Mock private PromoBannerRepository promoBannerRepository;

    @InjectMocks private PromoBannerService promoBannerService;

    private PromoBanner activeBanner;
    private PromoBanner inactiveBanner;

    @BeforeEach
    void setUp() {
        activeBanner = PromoBanner.builder()
                .id(1L)
                .imageUrl("https://cdn.example.com/banner1.jpg")
                .linkUrl("https://shop.example.com/sale")
                .active(true)
                .createdAt(LocalDateTime.of(2025, 1, 10, 9, 0))
                .build();

        inactiveBanner = PromoBanner.builder()
                .id(2L)
                .imageUrl("https://cdn.example.com/banner2.jpg")
                .linkUrl("https://shop.example.com/old")
                .active(false)
                .createdAt(LocalDateTime.of(2025, 1, 5, 8, 0))
                .build();
    }

    // ─── getActiveBanners ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getActiveBanners")
    class GetActiveBanners {

        @Test
        @DisplayName("returns only active banners ordered by createdAt ascending")
        void getActiveBanners_returnsActiveBannersOnly() {
            when(promoBannerRepository.findAllByActiveTrueOrderByCreatedAtAsc())
                    .thenReturn(List.of(activeBanner));

            List<PromoBannerResponse> result = promoBannerService.getActiveBanners();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).active()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when no active banners exist")
        void getActiveBanners_returnsEmptyListWhenNoneActive() {
            when(promoBannerRepository.findAllByActiveTrueOrderByCreatedAtAsc())
                    .thenReturn(List.of());

            assertThat(promoBannerService.getActiveBanners()).isEmpty();
        }

        @Test
        @DisplayName("maps all fields of each active banner correctly")
        void getActiveBanners_mapsAllFields() {
            when(promoBannerRepository.findAllByActiveTrueOrderByCreatedAtAsc())
                    .thenReturn(List.of(activeBanner));

            PromoBannerResponse response = promoBannerService.getActiveBanners().get(0);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/banner1.jpg");
            assertThat(response.linkUrl()).isEqualTo("https://shop.example.com/sale");
            assertThat(response.active()).isTrue();
            assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2025, 1, 10, 9, 0));
        }

        @Test
        @DisplayName("uses the dedicated active-only repository query method")
        void getActiveBanners_callsCorrectRepositoryMethod() {
            when(promoBannerRepository.findAllByActiveTrueOrderByCreatedAtAsc())
                    .thenReturn(List.of());

            promoBannerService.getActiveBanners();

            verify(promoBannerRepository).findAllByActiveTrueOrderByCreatedAtAsc();
        }

        @Test
        @DisplayName("returns multiple active banners when all are active")
        void getActiveBanners_returnsMultipleActiveBanners() {
            PromoBanner second = PromoBanner.builder()
                    .id(3L).imageUrl("img3.jpg").linkUrl("link3").active(true)
                    .createdAt(LocalDateTime.of(2025, 2, 1, 10, 0)).build();
            when(promoBannerRepository.findAllByActiveTrueOrderByCreatedAtAsc())
                    .thenReturn(List.of(activeBanner, second));

            assertThat(promoBannerService.getActiveBanners()).hasSize(2);
        }
    }

    // ─── getAllBanners ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllBanners")
    class GetAllBanners {

        @Test
        @DisplayName("returns both active and inactive banners")
        void getAllBanners_returnsBothActiveAndInactive() {
            when(promoBannerRepository.findAll()).thenReturn(List.of(activeBanner, inactiveBanner));

            List<PromoBannerResponse> result = promoBannerService.getAllBanners();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PromoBannerResponse::active).containsExactly(true, false);
        }

        @Test
        @DisplayName("returns empty list when repository has no banners")
        void getAllBanners_returnsEmptyList() {
            when(promoBannerRepository.findAll()).thenReturn(List.of());

            assertThat(promoBannerService.getAllBanners()).isEmpty();
        }

        @Test
        @DisplayName("maps all fields correctly for each banner")
        void getAllBanners_mapsAllFields() {
            when(promoBannerRepository.findAll()).thenReturn(List.of(inactiveBanner));

            PromoBannerResponse response = promoBannerService.getAllBanners().get(0);

            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/banner2.jpg");
            assertThat(response.linkUrl()).isEqualTo("https://shop.example.com/old");
            assertThat(response.active()).isFalse();
        }

        @Test
        @DisplayName("returns a single-element list when only one banner exists")
        void getAllBanners_returnsSingleElement() {
            when(promoBannerRepository.findAll()).thenReturn(List.of(activeBanner));

            assertThat(promoBannerService.getAllBanners()).hasSize(1);
        }

        @Test
        @DisplayName("calls repository findAll exactly once")
        void getAllBanners_callsFindAllOnce() {
            when(promoBannerRepository.findAll()).thenReturn(List.of());

            promoBannerService.getAllBanners();

            verify(promoBannerRepository).findAll();
        }
    }

    // ─── createBanner ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBanner")
    class CreateBanner {

        @Test
        @DisplayName("creates a banner and returns mapped response")
        void createBanner_success() {
            PromoBannerRequest request = new PromoBannerRequest("https://img.jpg", "https://link.com");
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> {
                PromoBanner b = inv.getArgument(0);
                b.setId(10L);
                return b;
            });

            PromoBannerResponse result = promoBannerService.createBanner(request);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.imageUrl()).isEqualTo("https://img.jpg");
            assertThat(result.linkUrl()).isEqualTo("https://link.com");
        }

        @Test
        @DisplayName("new banner is always created as active")
        void createBanner_isActiveByDefault() {
            PromoBannerRequest request = new PromoBannerRequest("img.jpg", null);
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.createBanner(request);

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("persists banner with imageUrl and linkUrl from request")
        void createBanner_savesCorrectFields() {
            ArgumentCaptor<PromoBanner> captor = ArgumentCaptor.forClass(PromoBanner.class);
            PromoBannerRequest request = new PromoBannerRequest("newimg.jpg", "newlink.com");
            when(promoBannerRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promoBannerService.createBanner(request);

            PromoBanner saved = captor.getValue();
            assertThat(saved.getImageUrl()).isEqualTo("newimg.jpg");
            assertThat(saved.getLinkUrl()).isEqualTo("newlink.com");
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("creates banner with null linkUrl when request linkUrl is null")
        void createBanner_withNullLinkUrl() {
            ArgumentCaptor<PromoBanner> captor = ArgumentCaptor.forClass(PromoBanner.class);
            PromoBannerRequest request = new PromoBannerRequest("img.jpg", null);
            when(promoBannerRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promoBannerService.createBanner(request);

            assertThat(captor.getValue().getLinkUrl()).isNull();
        }

        @Test
        @DisplayName("calls repository save exactly once")
        void createBanner_savesOnce() {
            PromoBannerRequest request = new PromoBannerRequest("img.jpg", "link.com");
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            promoBannerService.createBanner(request);

            verify(promoBannerRepository).save(any(PromoBanner.class));
        }
    }

    // ─── updateBanner ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateBanner")
    class UpdateBanner {

        @Test
        @DisplayName("updates imageUrl and linkUrl and returns mapped response")
        void updateBanner_success() {
            PromoBannerRequest request = new PromoBannerRequest("updated.jpg", "updated-link.com");
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.updateBanner(1L, request);

            assertThat(result.imageUrl()).isEqualTo("updated.jpg");
            assertThat(result.linkUrl()).isEqualTo("updated-link.com");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when banner id does not exist")
        void updateBanner_notFound() {
            PromoBannerRequest request = new PromoBannerRequest("img.jpg", "link.com");
            when(promoBannerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promoBannerService.updateBanner(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PromoBanner")
                    .hasMessageContaining("999");

            verify(promoBannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("preserves the active flag after update")
        void updateBanner_preservesActiveFlag() {
            PromoBannerRequest request = new PromoBannerRequest("img2.jpg", null);
            when(promoBannerRepository.findById(2L)).thenReturn(Optional.of(inactiveBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.updateBanner(2L, request);

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("saves the mutated banner entity to the repository")
        void updateBanner_savesCorrectEntity() {
            ArgumentCaptor<PromoBanner> captor = ArgumentCaptor.forClass(PromoBanner.class);
            PromoBannerRequest request = new PromoBannerRequest("fresh.jpg", "fresh-link.com");
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promoBannerService.updateBanner(1L, request);

            PromoBanner saved = captor.getValue();
            assertThat(saved.getImageUrl()).isEqualTo("fresh.jpg");
            assertThat(saved.getLinkUrl()).isEqualTo("fresh-link.com");
        }

        @Test
        @DisplayName("sets linkUrl to null when request linkUrl is null")
        void updateBanner_setsLinkUrlToNull() {
            PromoBannerRequest request = new PromoBannerRequest("img.jpg", null);
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.updateBanner(1L, request);

            assertThat(result.linkUrl()).isNull();
        }
    }

    // ─── deleteBanner ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteBanner")
    class DeleteBanner {

        @Test
        @DisplayName("deletes the banner when it exists")
        void deleteBanner_success() {
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));

            assertThatNoException().isThrownBy(() -> promoBannerService.deleteBanner(1L));

            verify(promoBannerRepository).delete(activeBanner);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when banner does not exist")
        void deleteBanner_notFound() {
            when(promoBannerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promoBannerService.deleteBanner(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PromoBanner")
                    .hasMessageContaining("999");

            verify(promoBannerRepository, never()).delete(any());
        }

        @Test
        @DisplayName("deletes the exact entity returned by findById")
        void deleteBanner_deletesCorrectEntity() {
            when(promoBannerRepository.findById(2L)).thenReturn(Optional.of(inactiveBanner));

            promoBannerService.deleteBanner(2L);

            verify(promoBannerRepository).delete(inactiveBanner);
        }

        @Test
        @DisplayName("calls findById before attempting deletion")
        void deleteBanner_lookupHappensBeforeDelete() {
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));

            promoBannerService.deleteBanner(1L);

            verify(promoBannerRepository).findById(1L);
        }

        @Test
        @DisplayName("does not call delete when banner is not found")
        void deleteBanner_noDeleteOnMissingId() {
            when(promoBannerRepository.findById(77L)).thenReturn(Optional.empty());

            try {
                promoBannerService.deleteBanner(77L);
            } catch (ResourceNotFoundException ignored) {
                // expected
            }

            verify(promoBannerRepository, never()).delete(any());
        }
    }

    // ─── toggleBanner ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleBanner")
    class ToggleBanner {

        @Test
        @DisplayName("sets banner to inactive when it is currently active")
        void toggleBanner_activeToInactive() {
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.toggleBanner(1L);

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("sets banner to active when it is currently inactive")
        void toggleBanner_inactiveToActive() {
            when(promoBannerRepository.findById(2L)).thenReturn(Optional.of(inactiveBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.toggleBanner(2L);

            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when banner does not exist")
        void toggleBanner_notFound() {
            when(promoBannerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promoBannerService.toggleBanner(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("PromoBanner")
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("persists the toggled state via save")
        void toggleBanner_persistsToggledState() {
            ArgumentCaptor<PromoBanner> captor = ArgumentCaptor.forClass(PromoBanner.class);
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            promoBannerService.toggleBanner(1L);

            assertThat(captor.getValue().isActive()).isFalse();
            verify(promoBannerRepository).save(any(PromoBanner.class));
        }

        @Test
        @DisplayName("returns full mapped response including id and urls after toggle")
        void toggleBanner_returnsMappedResponse() {
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoBannerResponse result = promoBannerService.toggleBanner(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/banner1.jpg");
            assertThat(result.linkUrl()).isEqualTo("https://shop.example.com/sale");
        }

        @Test
        @DisplayName("toggling twice restores original active state")
        void toggleBanner_doubleToggleRestoresOriginalState() {
            when(promoBannerRepository.findById(1L)).thenReturn(Optional.of(activeBanner));
            when(promoBannerRepository.save(any(PromoBanner.class))).thenAnswer(inv -> inv.getArgument(0));

            // First toggle: active → inactive
            PromoBannerResponse afterFirst = promoBannerService.toggleBanner(1L);
            assertThat(afterFirst.active()).isFalse();

            // activeBanner.active is now false (mutated by the service)
            // Second toggle: inactive → active
            PromoBannerResponse afterSecond = promoBannerService.toggleBanner(1L);
            assertThat(afterSecond.active()).isTrue();
        }
    }
}
