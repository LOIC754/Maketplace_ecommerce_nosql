package com.marketplace.delivery.api;

import jakarta.validation.constraints.NotBlank;

// Commande ShipOrder : corps de POST /deliveries/{orderId}/ship
public record ShipOrderRequest(@NotBlank String carrier, @NotBlank String trackingNumber) {}
