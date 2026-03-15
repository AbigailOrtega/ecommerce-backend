package com.ecommerce.service;

import com.ecommerce.dto.request.ShippingMethodRequest;
import com.ecommerce.dto.response.ShippingMethodResponse;
import com.ecommerce.entity.ShippingMethod;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ShippingMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingMethodService {

    private final ShippingMethodRepository repository;

    public List<ShippingMethodResponse> getActiveMethods() {
        return repository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toResponse).toList();
    }

    public List<ShippingMethodResponse> getAllMethods() {
        return repository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(this::toResponse).toList();
    }

    @Transactional
    public ShippingMethodResponse create(ShippingMethodRequest req) {
        ShippingMethod m = ShippingMethod.builder()
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .estimatedDays(req.estimatedDays())
                .displayOrder(req.displayOrder())
                .build();
        return toResponse(repository.save(m));
    }

    @Transactional
    public ShippingMethodResponse update(Long id, ShippingMethodRequest req) {
        ShippingMethod m = findById(id);
        m.setName(req.name());
        m.setDescription(req.description());
        m.setPrice(req.price());
        m.setEstimatedDays(req.estimatedDays());
        m.setDisplayOrder(req.displayOrder());
        return toResponse(repository.save(m));
    }

    @Transactional
    public ShippingMethodResponse toggle(Long id) {
        ShippingMethod m = findById(id);
        m.setActive(!m.isActive());
        return toResponse(repository.save(m));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }

    public ShippingMethod findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingMethod", "id", id));
    }

    private ShippingMethodResponse toResponse(ShippingMethod m) {
        return new ShippingMethodResponse(m.getId(), m.getName(), m.getDescription(),
                m.getPrice(), m.getEstimatedDays(), m.isActive(), m.getDisplayOrder());
    }
}
