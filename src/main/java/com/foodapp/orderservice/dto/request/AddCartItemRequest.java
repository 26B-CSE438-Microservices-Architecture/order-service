package com.foodapp.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddCartItemRequest(
        @NotNull UUID menuItemId,
        UUID restaurantId,
        @Min(1) int quantity,
        String specialInstructions
) {
    @JsonCreator
    public static AddCartItemRequest of(
            @JsonProperty("menuItemId") @JsonAlias({"productId", "product_id", "menu_item_id"}) String menuItemIdRaw,
            @JsonProperty("restaurantId") @JsonAlias({"vendorId", "vendor_id", "restaurant_id"}) String restaurantIdRaw,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("specialInstructions") @JsonAlias({"special_instructions", "notes"}) String specialInstructions) {
        return new AddCartItemRequest(
                parseUuidLenient(menuItemIdRaw),
                parseUuidLenient(restaurantIdRaw),
                quantity,
                specialInstructions
        );
    }

    private static UUID parseUuidLenient(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
