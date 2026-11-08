package com.marketplace.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

// Commande PlaceOrder : corps de POST /orders
public record PlaceOrderRequest(
        @NotBlank String orderId,
        @NotBlank String customerId,
        @NotEmpty List<@Valid Line> lines,          // au moins une ligne (invariant)
        @NotBlank String deliveryAddress
) {
    public record Line(
            @NotBlank String productId,
            @NotBlank String productName,
            @Positive int quantity,                  // quantité > 0 (invariant)
            @PositiveOrZero double unitPrice
    ) {}
}
