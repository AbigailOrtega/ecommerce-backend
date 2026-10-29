package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductColor;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductSizeRepository productSizeRepository;
    @InjectMocks StockService stockService;

    private final Product simple = Product.builder().id(1L).name("Gorra").colors(new ArrayList<>()).build();
    private final ProductSize size = ProductSize.builder().id(20L).name("M").build();
    private final Product withColors = Product.builder().id(2L).name("Playera")
            .colors(new ArrayList<>(List.of(new ProductColor()))).build();

    @Test
    @DisplayName("reserve takes stock from the product when it has no colors")
    void reserve_simple() {
        when(productRepository.decrementStock(1L, 2)).thenReturn(1);

        stockService.reserve(simple, null, 2);

        verifyNoInteractions(productSizeRepository);
    }

    @Test
    @DisplayName("reserve takes stock from the size when the product has colors")
    void reserve_size() {
        when(productSizeRepository.decrementStock(20L, 3)).thenReturn(1);

        stockService.reserve(withColors, size, 3);

        verify(productRepository, never()).decrementStock(any(), anyInt());
    }

    @Test
    @DisplayName("reserve throws when the atomic update finds no stock left")
    void reserve_outOfStock() {
        when(productSizeRepository.decrementStock(20L, 1)).thenReturn(0);

        assertThatThrownBy(() -> stockService.reserve(withColors, size, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Playera – talla M");
    }

    @Test
    @DisplayName("reserve throws when a product with colors has no size selected")
    void reserve_missingSize() {
        assertThatThrownBy(() -> stockService.reserve(withColors, null, 1))
                .isInstanceOf(BadRequestException.class);
    }

    private Order orderWith(OrderItem... items) {
        return Order.builder().items(new ArrayList<>(List.of(items))).build();
    }

    @Test
    @DisplayName("restore gives back product and size stock")
    void restore() {
        Order order = orderWith(
                OrderItem.builder().product(simple).quantity(2).build(),
                OrderItem.builder().product(withColors).selectedSize(size).quantity(5).build());

        stockService.restore(order, OrderStatus.CONFIRMED);

        verify(productRepository).incrementStock(1L, 2);
        verify(productSizeRepository).incrementStock(20L, 5);
    }

    @Test
    @DisplayName("restore does nothing if the stock was already returned (CANCELLED / REFUNDED)")
    void restore_noDoubleCount() {
        Order order = orderWith(OrderItem.builder().product(simple).quantity(2).build());

        stockService.restore(order, OrderStatus.CANCELLED);
        stockService.restore(order, OrderStatus.REFUNDED);

        verifyNoInteractions(productRepository, productSizeRepository);
    }
}
