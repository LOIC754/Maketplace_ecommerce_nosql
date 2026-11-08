package com.marketplace.order.domain;

// Ligne de commande, incluse dans l'agrégat Commande.
// productName et unitPrice sont une copie figée au moment de l'achat.
public record OrderLine(String productId, String productName, int quantity, double unitPrice) {

    // Montant de la ligne
    public double amount() {
        return quantity * unitPrice;
    }
}
