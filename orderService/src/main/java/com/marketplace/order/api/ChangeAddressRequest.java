package com.marketplace.order.api;

import jakarta.validation.constraints.NotBlank;

// Commande ChangeDeliveryAddress : corps de PATCH /orders/{orderId}/address
public record ChangeAddressRequest(@NotBlank String deliveryAddress) {}
