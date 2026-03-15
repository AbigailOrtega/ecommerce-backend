package com.ecommerce.service;

import com.ecommerce.dto.request.PromoBannerRequest;
import com.ecommerce.dto.response.PromoBannerResponse;
import com.ecommerce.entity.PromoBanner;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.PromoBannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromoBannerService {

    private final PromoBannerRepository promoBannerRepository;

    public List<PromoBannerResponse> getActiveBanners() {
        return promoBannerRepository.findAllByActiveTrueOrderByCreatedAtAsc()
                .stream().map(this::mapToResponse).toList();
    }

    public List<PromoBannerResponse> getAllBanners() {
        return promoBannerRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public PromoBannerResponse createBanner(PromoBannerRequest request) {
        PromoBanner banner = PromoBanner.builder()
                .imageUrl(request.imageUrl())
                .linkUrl(request.linkUrl())
                .active(true)
                .build();
        return mapToResponse(promoBannerRepository.save(banner));
    }

    @Transactional
    public PromoBannerResponse updateBanner(Long id, PromoBannerRequest request) {
        PromoBanner banner = findById(id);
        banner.setImageUrl(request.imageUrl());
        banner.setLinkUrl(request.linkUrl());
        return mapToResponse(promoBannerRepository.save(banner));
    }

    @Transactional
    public void deleteBanner(Long id) {
        promoBannerRepository.delete(findById(id));
    }

    @Transactional
    public PromoBannerResponse toggleBanner(Long id) {
        PromoBanner banner = findById(id);
        banner.setActive(!banner.isActive());
        return mapToResponse(promoBannerRepository.save(banner));
    }

    private PromoBanner findById(Long id) {
        return promoBannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromoBanner", "id", id));
    }

    private PromoBannerResponse mapToResponse(PromoBanner banner) {
        return new PromoBannerResponse(
                banner.getId(),
                banner.getImageUrl(),
                banner.getLinkUrl(),
                banner.isActive(),
                banner.getCreatedAt()
        );
    }
}
