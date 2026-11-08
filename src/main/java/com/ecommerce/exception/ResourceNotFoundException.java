package com.ecommerce.exception;

import java.util.Map;

public class ResourceNotFoundException extends RuntimeException {

    // These messages reach shoppers, so the entity name is shown in Spanish.
    private static final Map<String, String> NOT_FOUND = Map.ofEntries(
            Map.entry("CartItem", "Producto del carrito no encontrado"),
            Map.entry("Category", "Categoría no encontrada"),
            Map.entry("Coupon", "Cupón no encontrado"),
            Map.entry("Order", "Pedido no encontrado"),
            Map.entry("PickupAvailability", "Horario de recolección no encontrado"),
            Map.entry("PickupException", "Excepción de horario no encontrada"),
            Map.entry("PickupLocation", "Punto de recolección no encontrado"),
            Map.entry("Product", "Producto no encontrado"),
            Map.entry("ProductSize", "Talla no encontrada"),
            Map.entry("PromoBanner", "Banner no encontrado"),
            Map.entry("Promotion", "Promoción no encontrada"),
            Map.entry("Review", "Reseña no encontrada"),
            Map.entry("ShippingMethod", "Método de envío no encontrado"),
            Map.entry("Ticket", "Ticket no encontrado"),
            Map.entry("Token", "El enlace no es válido o ya expiró"),
            Map.entry("User", "Usuario no encontrado"));

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(NOT_FOUND.getOrDefault(resource, "No encontrado: " + resource));
    }
}
