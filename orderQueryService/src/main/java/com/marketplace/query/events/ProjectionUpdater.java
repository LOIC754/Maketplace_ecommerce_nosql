package com.marketplace.query.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.query.domain.OrderViewRepository;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Projecteur : applique UN événement à la projection order_views.
// Utilisé par la consommation normale ET par la reconstruction depuis l'archive.
@Component
public class ProjectionUpdater {

    private final ObjectMapper json;
    private final OrderViewRepository views;

    public ProjectionUpdater(ObjectMapper json, OrderViewRepository views) {
        this.json = json;
        this.views = views;
    }

    // Retourne une description du résultat (pour les logs)
    public String apply(String raw) throws Exception {
        JsonNode e = json.readTree(raw);
        String eventId = e.get("eventId").asText();
        String type = e.get("eventType").asText();
        JsonNode p = e.get("payload");
        String orderId = p.get("orderId").asText();
        Instant at = Instant.parse(e.get("occurredAt").asText());

        boolean applied = switch (type) {
            case "OrderCreated" -> {
                List<Document> items = new ArrayList<>();
                for (JsonNode l : p.get("lines")) {               // prix unitaires retirés
                    items.add(new Document("productName", l.get("productName").asText())
                            .append("quantity", l.get("quantity").asInt()));
                }
                Instant createdAt = p.has("createdAt") ? Instant.parse(p.get("createdAt").asText()) : at;
                yield views.create(eventId, orderId, p.get("customerId").asText(), createdAt,
                        p.get("total").asDouble(), items, p.get("deliveryAddress").asText());
            }
            case "StockReserved" -> views.changeStatus(eventId, orderId, "CONFIRMED", "Stock réservé", at, Map.of());
            case "StockRejected" -> views.changeStatus(eventId, orderId, "REJECTED", "Stock insuffisant", at, Map.of());
            case "DeliveryAddressChanged" ->
                    views.changeAddress(eventId, orderId, p.get("deliveryAddress").asText(), at);
            case "OrderShipped" -> views.changeStatus(eventId, orderId, "SHIPPED", "Expédiée", at,
                    Map.of("carrier", p.get("carrier").asText(), "trackingNumber", p.get("trackingNumber").asText()));
            default -> false;
        };
        return eventId + " " + type + " " + orderId + " -> " + (applied ? "vue mise à jour" : "déjà appliqué, ignoré");
    }
}
