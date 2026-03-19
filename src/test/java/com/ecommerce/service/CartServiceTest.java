package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("CartService")
class CartServiceTest {

    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSizeRepository productSizeRepository;
    @Mock private UserRepository userRepository;
    @Mock private PromotionRepository promotionRepository;

    @InjectMocks private CartService cartService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).firstName("Ana").lastName("López")
                .email("ana@example.com").role(Role.CUSTOMER).enabled(true)
                .build();

        product = Product.builder()
                .id(10L).name("Camiseta").price(BigDecimal.valueOf(99.00))
                .stockQuantity(20).active(true).build();
    }

    // ─── getCartItems ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCartItems")
    class GetCartItems {

        @Test
        @DisplayName("returns mapped responses for all user cart items")
        void getCartItems_success() {
            CartItem item = CartItem.builder()
                    .id(100L).user(user).product(product).quantity(2).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findByUserId(1L)).thenReturn(List.of(item));
            when(promotionRepository.findBestActivePromotion(eq(10L), any())).thenReturn(Optional.empty());

            List<CartItemResponse> result = cartService.getCartItems("ana@example.com");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).quantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns empty list when cart has no items")
        void getCartItems_empty() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findByUserId(1L)).thenReturn(List.of());

            List<CartItemResponse> result = cartService.getCartItems("ana@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user does not exist")
        void getCartItems_userNotFound() {
            when(userRepository.findByEmail("noone@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.getCartItems("noone@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── addToCart ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addToCart")
    class AddToCart {

        @Test
        @DisplayName("adds new item when product has no colors and stock is sufficient")
        void addToCart_newItem_success() {
            CartItemRequest request = new CartItemRequest(10L, 1, null);

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByUserIdAndProductIdAndSelectedSizeIsNull(1L, 10L))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                CartItem ci = inv.getArgument(0);
                ci.setId(200L);
                return ci;
            });
            when(promotionRepository.findBestActivePromotion(eq(10L), any())).thenReturn(Optional.empty());

            CartItemResponse response = cartService.addToCart("ana@example.com", request);

            assertThat(response).isNotNull();
            assertThat(response.quantity()).isEqualTo(1);
            verify(cartItemRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("accumulates quantity when item already exists in cart")
        void addToCart_existingItem_accumulatesQuantity() {
            CartItemRequest request = new CartItemRequest(10L, 3, null);
            CartItem existing = CartItem.builder()
                    .id(200L).user(user).product(product).quantity(2).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(cartItemRepository.findByUserIdAndProductIdAndSelectedSizeIsNull(1L, 10L))
                    .thenReturn(Optional.of(existing));
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(promotionRepository.findBestActivePromotion(eq(10L), any())).thenReturn(Optional.empty());

            cartService.addToCart("ana@example.com", request);

            // existing.quantity should now be 2 + 3 = 5
            assertThat(existing.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("throws BadRequestException when insufficient stock (no colors)")
        void addToCart_insufficientStock() {
            product.setStockQuantity(1);
            CartItemRequest request = new CartItemRequest(10L, 5, null);

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> cartService.addToCart("ana@example.com", request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient stock");

            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BadRequestException when size stock is insufficient")
        void addToCart_sizeInsufficientStock() {
            ProductSize size = new ProductSize();
            size.setId(50L);
            size.setName("M");
            size.setStock(1);

            CartItemRequest request = new CartItemRequest(10L, 3, 50L);

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productSizeRepository.findById(50L)).thenReturn(Optional.of(size));

            assertThatThrownBy(() -> cartService.addToCart("ana@example.com", request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Insufficient stock for this size");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product does not exist")
        void addToCart_productNotFound() {
            CartItemRequest request = new CartItemRequest(999L, 1, null);

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addToCart("ana@example.com", request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("adds item with specific size when sizeId is provided and stock is sufficient")
        void addToCart_withSize_success() {
            ProductColor color = new ProductColor();
            color.setId(1L);
            color.setName("Rojo");

            ProductSize size = new ProductSize();
            size.setId(50L);
            size.setName("L");
            size.setStock(10);
            size.setColor(color);

            CartItemRequest request = new CartItemRequest(10L, 2, 50L);

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(10L)).thenReturn(Optional.of(product));
            when(productSizeRepository.findById(50L)).thenReturn(Optional.of(size));
            when(cartItemRepository.findByUserIdAndProductIdAndSelectedSizeId(1L, 10L, 50L))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                CartItem ci = inv.getArgument(0);
                ci.setId(300L);
                return ci;
            });
            when(promotionRepository.findBestActivePromotion(eq(10L), any())).thenReturn(Optional.empty());

            CartItemResponse response = cartService.addToCart("ana@example.com", request);

            assertThat(response).isNotNull();
            assertThat(response.selectedSizeName()).isEqualTo("L");
        }
    }

    // ─── updateCartItem ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCartItem")
    class UpdateCartItem {

        @Test
        @DisplayName("updates quantity when positive")
        void updateCartItem_success() {
            CartItem item = CartItem.builder()
                    .id(100L).user(user).product(product).quantity(1).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
            when(promotionRepository.findBestActivePromotion(eq(10L), any())).thenReturn(Optional.empty());

            CartItemResponse response = cartService.updateCartItem("ana@example.com", 100L, 4);

            assertThat(response).isNotNull();
            assertThat(item.getQuantity()).isEqualTo(4);
        }

        @Test
        @DisplayName("deletes item and returns null when quantity is 0")
        void updateCartItem_zeroQuantity_deletesItem() {
            CartItem item = CartItem.builder()
                    .id(100L).user(user).product(product).quantity(2).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

            CartItemResponse response = cartService.updateCartItem("ana@example.com", 100L, 0);

            assertThat(response).isNull();
            verify(cartItemRepository).delete(item);
            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("deletes item and returns null when quantity is negative")
        void updateCartItem_negativeQuantity_deletesItem() {
            CartItem item = CartItem.builder()
                    .id(100L).user(user).product(product).quantity(2).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

            CartItemResponse response = cartService.updateCartItem("ana@example.com", 100L, -1);

            assertThat(response).isNull();
            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("throws BadRequestException when item belongs to another user")
        void updateCartItem_wrongUser() {
            User otherUser = User.builder().id(99L).email("other@example.com").build();
            CartItem item = CartItem.builder()
                    .id(100L).user(otherUser).product(product).quantity(1).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> cartService.updateCartItem("ana@example.com", 100L, 3))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cart item does not belong to user");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when item does not exist")
        void updateCartItem_itemNotFound() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateCartItem("ana@example.com", 999L, 1))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── removeFromCart ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeFromCart")
    class RemoveFromCart {

        @Test
        @DisplayName("deletes the item when it belongs to the user")
        void removeFromCart_success() {
            CartItem item = CartItem.builder()
                    .id(100L).user(user).product(product).quantity(1).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

            cartService.removeFromCart("ana@example.com", 100L);

            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("throws BadRequestException when item belongs to another user")
        void removeFromCart_wrongUser() {
            User otherUser = User.builder().id(99L).email("other@example.com").build();
            CartItem item = CartItem.builder()
                    .id(100L).user(otherUser).product(product).quantity(1).build();

            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> cartService.removeFromCart("ana@example.com", 100L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cart item does not belong to user");

            verify(cartItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when item does not exist")
        void removeFromCart_itemNotFound() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
            when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeFromCart("ana@example.com", 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── clearCart ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clearCart")
    class ClearCart {

        @Test
        @DisplayName("calls deleteByUserId with the correct user id")
        void clearCart_success() {
            when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

            cartService.clearCart("ana@example.com");

            verify(cartItemRepository).deleteByUserId(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user does not exist")
        void clearCart_userNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.clearCart("ghost@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(cartItemRepository, never()).deleteByUserId(any());
        }
    }
}
