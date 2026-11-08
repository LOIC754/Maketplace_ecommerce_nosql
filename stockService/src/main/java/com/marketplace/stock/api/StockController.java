package com.marketplace.stock.api;

import com.marketplace.stock.domain.StockRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// API REST du service Stock
@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockRepository stock;

    public StockController(StockRepository stock) {
        this.stock = stock;
    }

    // Catalogue complet avec les disponibilités
    @GetMapping
    public List<Map<Object, Object>> catalog() {
        return stock.findAll();
    }

    // R2 : GET /stock/{productId}
    @GetMapping("/{productId}")
    public ResponseEntity<Map<Object, Object>> get(@PathVariable String productId) {
        Map<Object, Object> product = stock.find(productId);
        return product.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(product);
    }

    // W4 : POST /stock/{productId}/restock (commande RestockProduct)
    @PostMapping("/{productId}/restock")
    public ResponseEntity<Map<String, Object>> restock(@PathVariable String productId,
                                                       @Valid @RequestBody RestockRequest req) {
        Long available = stock.restock(productId, req.quantity());
        return available == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(Map.of("productId", productId, "available", available));
    }
}
