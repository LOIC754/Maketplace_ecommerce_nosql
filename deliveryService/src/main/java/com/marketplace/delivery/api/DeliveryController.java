package com.marketplace.delivery.api;

import com.marketplace.delivery.domain.DeliveryRepository;
import com.marketplace.delivery.events.EventPublisher;
import jakarta.validation.Valid;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// API REST du service Livraison
@RestController
public class DeliveryController {

    private final DeliveryRepository repo;
    private final EventPublisher publisher;

    public DeliveryController(DeliveryRepository repo, EventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    // R3 : GET /deliveries/to-prepare -> livraisons à préparer avec adresse et articles
    @GetMapping("/deliveries/to-prepare")
    public List<Document> toPrepare() {
        return repo.toPrepare();
    }

    // GET /deliveries/{orderId} -> la livraison d'une commande
    @GetMapping("/deliveries/{orderId}")
    public ResponseEntity<Document> delivery(@PathVariable String orderId) {
        Document d = repo.findDelivery(orderId);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(clean(d));
    }

    // GET /replicas/{orderId} -> le réplica de la commande vu par Livraison
    @GetMapping("/replicas/{orderId}")
    public ResponseEntity<Document> replica(@PathVariable String orderId) {
        Document r = repo.findReplica(orderId);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(clean(r));
    }

    // W5 : POST /deliveries/{orderId}/ship (commande ShipOrder) -> OrderShipped_v1
    @PostMapping("/deliveries/{orderId}/ship")
    public ResponseEntity<Map<String, Object>> ship(@PathVariable String orderId,
                                                    @Valid @RequestBody ShipOrderRequest req) {
        // Autonomie : le statut de la commande est lu dans le RÉPLICA local,
        // sans interroger le service Commande
        Document replica = repo.findReplica(orderId);
        if (replica == null) return ResponseEntity.notFound().build();
        if (!"CONFIRMED".equals(replica.getString("orderStatus"))) {
            return conflict("Commande " + orderId + " non confirmée (" + replica.getString("orderStatus") + ")");
        }
        if (!repo.ship(orderId, req.carrier(), req.trackingNumber())) {
            return conflict("Livraison de " + orderId + " inexistante ou déjà expédiée");
        }
        Document delivery = repo.findDelivery(orderId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("deliveryId", delivery.getString("deliveryId"));
        payload.put("carrier", req.carrier());
        payload.put("trackingNumber", req.trackingNumber());
        String eventId = publisher.publish("OrderShipped", delivery.getString("deliveryId"), payload);
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", "SHIPPED", "eventId", eventId));
    }

    private ResponseEntity<Map<String, Object>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", message));
    }

    // Retire l'_id technique de MongoDB de la réponse
    private Document clean(Document d) {
        d.remove("_id");
        return d;
    }
}
