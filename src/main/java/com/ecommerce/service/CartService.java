package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductSizeRepository;
import com.ecommerce.repository.PromotionRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
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

        // Resolve selected size when sizeId is provided
        ProductSize selectedSize = null;
        if (request.sizeId() != null) {
            selectedSize = productSizeRepository.findById(request.sizeId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductSize", "id", request.sizeId()));
        }

        // Look up existing cart item for same product+size combination
        Optional<CartItem> existingItem = request.sizeId() != null
                ? cartItemRepository.findByUserIdAndProductIdAndSelectedSizeId(user.getId(), product.getId(), request.sizeId())
                : cartItemRepository.findByUserIdAndProductIdAndSelectedSizeIsNull(user.getId(), product.getId());

        // what is already in the cart counts too, otherwise adding one at a time skips the limit
        int alreadyInCart = existingItem.map(CartItem::getQuantity).orElse(0);
        checkStock(product, selectedSize, alreadyInCart + request.quantity());

        CartItem cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + request.quantity());
        } else {
            cartItem = CartItem.builder()
                    .user(user)
                    .product(product)
                    .quantity(request.quantity())
                    .selectedSize(selectedSize)
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
            throw new BadRequestException("Este producto no está en tu carrito");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(cartItem);
            return null;
        }

        checkStock(cartItem.getProduct(), cartItem.getSelectedSize(), quantity);
        cartItem.setQuantity(quantity);
        return mapToResponse(cartItemRepository.save(cartItem));
    }

    /** Rejects a cart quantity above what is in stock, telling the shopper how many are left. */
    private void checkStock(Product product, ProductSize size, int wanted) {
        Integer available = size != null ? size.getStock()
                : product.getColors().isEmpty() ? product.getStockQuantity() : null;
        if (available == null || wanted <= available) return;

        String what = product.getName() + (size != null ? " (talla " + size.getName() + ")" : "");
        throw new BadRequestException(available <= 0 ? what + " está agotado"
                : available == 1 ? "Solo queda 1 disponible de " + what
                : "Solo quedan " + available + " disponibles de " + what);
    }

    @Transactional
    public void removeFromCart(String email, Long itemId) {
        User user = findUserByEmail(email);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Este producto no está en tu carrito");
        }

        cartItemRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(String email) {
        User user = findUserByEmail(email);
        cartItemRepository.deleteByUserId(user.getId());
    }

    /**
     * Puts a cancelled order's items back in its owner's cart, so a shopper whose payment failed
     * can try again without rebuilding the cart. Guest carts live in the browser and are untouched.
     */
    @Transactional
    public void restoreFromOrder(Order order) {
        User user = order.getUser();
        if (user == null) return;
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || !product.isActive()) continue;
            ProductSize size = item.getSelectedSize();
            Optional<CartItem> existing = size != null
                    ? cartItemRepository.findByUserIdAndProductIdAndSelectedSizeId(user.getId(), product.getId(), size.getId())
                    : cartItemRepository.findByUserIdAndProductIdAndSelectedSizeIsNull(user.getId(), product.getId());
            CartItem cartItem = existing.orElseGet(() -> CartItem.builder()
                    .user(user).product(product).selectedSize(size).quantity(0).build());
            cartItem.setQuantity(cartItem.getQuantity() + item.getQuantity());
            cartItemRepository.save(cartItem);
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public CartItemResponse mapToResponse(CartItem cartItem) {
        Product product = cartItem.getProduct();
        List<CategoryResponse> categoryResponses = product.getCategories().stream()
                .map(cat -> new CategoryResponse(cat.getId(), cat.getName(), cat.getDescription(), cat.getImageUrl(), cat.getSlug()))
                .toList();

        List<com.ecommerce.dto.response.ProductColorResponse> colorResponses = product.getColors().stream()
                .map(c -> new com.ecommerce.dto.response.ProductColorResponse(c.getId(), c.getName(), c.getImages(),
                        c.getSizes().stream()
                                .map(s -> new com.ecommerce.dto.response.ProductSizeResponse(s.getId(), s.getName(), s.getStock()))
                                .toList()))
                .toList();

        // Apply active promotion if any
        Optional<Promotion> promo = promotionRepository
                .findBestActivePromotion(product.getId(), LocalDate.now());
        BigDecimal discountedPrice = promo.map(p ->
                product.getPrice()
                        .multiply(BigDecimal.ONE.subtract(p.getDiscountPercent().divide(BigDecimal.valueOf(100))))
                        .setScale(2, RoundingMode.HALF_UP)
        ).orElse(null);

        ProductResponse productResponse = new ProductResponse(
                product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getCompareAtPrice(), product.getSku(),
                product.getStockQuantity(), product.getImageUrl(), product.getImages(),
                product.getSlug(), product.isFeatured(), product.isActive(),
                categoryResponses, product.getCreatedAt(), colorResponses,
                discountedPrice,
                promo.map(Promotion::getDiscountPercent).orElse(null),
                promo.map(Promotion::getName).orElse(null)
        );

        String selectedColorName = cartItem.getSelectedSize() != null
                ? cartItem.getSelectedSize().getColor().getName() : null;
        String selectedSizeName = cartItem.getSelectedSize() != null
                ? cartItem.getSelectedSize().getName() : null;

        BigDecimal unitPrice = discountedPrice != null ? discountedPrice : product.getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return new CartItemResponse(cartItem.getId(), productResponse, cartItem.getQuantity(), subtotal,
                selectedColorName, selectedSizeName);
    }
}
