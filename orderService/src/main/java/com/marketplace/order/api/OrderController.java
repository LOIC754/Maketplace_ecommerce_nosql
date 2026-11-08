package com.marketplace.order.api;

import com.marketplace.order.domain.OrderLine;
import com.marketplace.order.domain.OrderService;
import com.marketplace.order.domain.OrderService.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// API REST du service Commande
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    // W1 : POST /orders (commande PlaceOrder)
    // 202 Accepted : la commande est enregistrée PENDING et l'événement publié,
    // mais le stock n'est pas encore réservé (traitement asynchrone).
    @PostMapping
    public ResponseEntity<Map<String, Object>> place(@Valid @RequestBody PlaceOrderRequest req) {
        var lines = req.lines().stream()
                .map(l -> new OrderLine(l.productId(), l.productName(), l.quantity(), l.unitPrice()))
                .toList();
        Result r = service.placeOrder(req.orderId(), req.customerId(), lines, req.deliveryAddress());
        return switch (r.outcome()) {
            case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("orderId", req.orderId(), "status", "PENDING", "eventId", r.eventId()));
            default -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Commande déjà existante : " + req.orderId()));
        };
    }

    // W2 : PATCH /orders/{orderId}/address (commande ChangeDeliveryAddress)
    @PatchMapping("/{orderId}/address")
    public ResponseEntity<Map<String, Object>> changeAddress(@PathVariable String orderId,
                                                             @Valid @RequestBody ChangeAddressRequest req) {
        Result r = service.changeAddress(orderId, req.deliveryAddress());
        return switch (r.outcome()) {
            case ACCEPTED -> ResponseEntity.ok(Map.of("orderId", orderId, "eventId", r.eventId()));
            case NOT_FOUND -> ResponseEntity.notFound().build();
            default -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Adresse non modifiable : commande expédiée ou refusée"));
        };
    }

    // R1 : GET /orders/{orderId}
    @GetMapping("/{orderId}")
    public ResponseEntity<?> get(@PathVariable String orderId) {
        return service.find(orderId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
