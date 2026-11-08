package com.marketplace.stock.api;

import jakarta.validation.constraints.Positive;

// Commande RestockProduct : corps de POST /stock/{productId}/restock
public record RestockRequest(@Positive int quantity) {}
