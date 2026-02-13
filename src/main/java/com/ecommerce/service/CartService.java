package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public List<CartItemResponse> getCartItems(String email) {
        User user = findUserByEmail(email);
        return cartItemRepository.findByUserId(user.getId())
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public CartItemResponse addToCart(String email, CartItemRequest request) {
        User user = findUserByEmail(email);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.productId()));

        if (product.getStockQuantity() < request.quantity()) {
            throw new BadRequestException("Insufficient stock for product: " + product.getName());
        }

        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId());

        CartItem cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + request.quantity());
        } else {
            cartItem = CartItem.builder()
                    .user(user)
                    .product(product)
                    .quantity(request.quantity())
                    .build();
        }

        return mapToResponse(cartItemRepository.save(cartItem));
    }

    @Transactional
    public CartItemResponse updateCartItem(String email, Long itemId, int quantity) {
        User user = findUserByEmail(email);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Cart item does not belong to user");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(cartItem);
            return null;
        }

        cartItem.setQuantity(quantity);
        return mapToResponse(cartItemRepository.save(cartItem));
    }

    @Transactional
    public void removeFromCart(String email, Long itemId) {
        User user = findUserByEmail(email);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Cart item does not belong to user");
        }

        cartItemRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(String email) {
        User user = findUserByEmail(email);
        cartItemRepository.deleteByUserId(user.getId());
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private CartItemResponse mapToResponse(CartItem cartItem) {
        Product product = cartItem.getProduct();
        CategoryResponse categoryResponse = null;
        if (product.getCategory() != null) {
            Category cat = product.getCategory();
            categoryResponse = new CategoryResponse(cat.getId(), cat.getName(), cat.getDescription(), cat.getImageUrl(), cat.getSlug());
        }

        ProductResponse productResponse = new ProductResponse(
                product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getCompareAtPrice(), product.getSku(),
                product.getStockQuantity(), product.getImageUrl(), product.getImages(),
                product.getSlug(), product.isFeatured(), product.isActive(),
                categoryResponse, product.getCreatedAt()
        );

        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return new CartItemResponse(cartItem.getId(), productResponse, cartItem.getQuantity(), subtotal);
    }
}
