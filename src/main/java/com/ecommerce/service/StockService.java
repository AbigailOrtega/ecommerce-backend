package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductSize;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Stock changes go through atomic UPDATE statements so two concurrent orders can't both take
 * the last unit (no read-modify-write on the entity). Products without colors keep stock on
 * the product; products with colors keep it per size.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    /** Statuses whose stock has already been given back; restoring again would double-count. */
    private static final Set<OrderStatus> STOCK_RETURNED = Set.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;

    /** Takes {@code qty} units or throws if another order got them first. */
    public void reserve(Product product, ProductSize size, int qty) {
        int updated;
        if (product.getColors().isEmpty()) {
            updated = productRepository.decrementStock(product.getId(), qty);
        } else {
            updated = size != null ? productSizeRepository.decrementStock(size.getId(), qty) : 0;
        }
        if (updated == 0) {
            throw new BadRequestException("Stock insuficiente para: " + product.getName()
                    + (size != null ? " – talla " + size.getName() : ""));
        }
    }

    /**
     * Gives the order's units back, unless {@code previousStatus} means that already happened.
     * Call it before or after changing the order's status, passing the status it had before.
     */
    public void restore(Order order, OrderStatus previousStatus) {
        if (STOCK_RETURNED.contains(previousStatus)) return;
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null) continue;
            if (product.getColors().isEmpty()) {
                productRepository.incrementStock(product.getId(), item.getQuantity());
            } else if (item.getSelectedSize() != null) {
                productSizeRepository.incrementStock(item.getSelectedSize().getId(), item.getQuantity());
            }
        }
    }
}
