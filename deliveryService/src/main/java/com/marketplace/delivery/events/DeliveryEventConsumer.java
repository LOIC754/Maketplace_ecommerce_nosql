package com.marketplace.delivery.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.delivery.domain.DeliveryRepository;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Consommation des événements destinés au service Livraison :
// OrderCreated (crée le réplica), StockReserved / StockRejected (statut du réplica,
// création de la livraison), DeliveryAddressChanged (adresse du réplica)
@Component
public class DeliveryEventConsumer {

    static final String READY = "shop:delivery:ready";
    static final String PROCESSING = "shop:delivery:processing";

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventConsumer.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final DeliveryRepository repo;

    public DeliveryEventConsumer(StringRedisTemplate redis, ObjectMapper json, DeliveryRepository repo) {
        this.redis = redis;
        this.json = json;
        this.repo = repo;
    }

    // Reprise après panne, AVANT le lancement de la lecture périodique
    @PostConstruct
    public void recover() {
        int n = 0;
        while (redis.opsForList().move(PROCESSING, Direction.last(), READY, Direction.first()) != null) n++;
        if (n > 0) log.info("Reprise : {} événement(s) remis dans {}", n, READY);
    }

    @Scheduled(fixedDelayString = "${delivery.consumer.poll-delay-ms:500}")
    public void consumeOne() {
        // LMOVE shop:delivery:ready shop:delivery:processing LEFT RIGHT (atomique)
        String raw = redis.opsForList().move(READY, Direction.first(), PROCESSING, Direction.last());
        if (raw == null) return;

        try {
            JsonNode e = json.readTree(raw);
            String eventId = e.get("eventId").asText();
            String type = e.get("eventType").asText();
            JsonNode p = e.get("payload");
            String orderId = p.get("orderId").asText();
            Instant at = Instant.parse(e.get("occurredAt").asText());

            String result = switch (type) {
                case "OrderCreated" -> {
                    // Le réplica ne garde que ce qui est utile à la livraison (ni prix, ni client)
                    List<Document> items = new ArrayList<>();
                    for (JsonNode l : p.get("lines")) {
                        items.add(new Document("productId", l.get("productId").asText())
                                .append("productName", l.get("productName").asText())
                                .append("quantity", l.get("quantity").asInt()));
                    }
                    yield repo.createReplica(eventId, orderId, p.get("deliveryAddress").asText(), items, at)
                            ? "réplica créé" : "réplica déjà existant, ignoré";
                }
                case "StockReserved" -> {
                    boolean updated = repo.updateReplica(eventId, orderId, "orderStatus", "CONFIRMED", at);
                    // Livraison créée même si le réplica était déjà à jour (reprise après panne)
                    boolean created = repo.createDelivery(orderId, at);
                    yield (updated ? "réplica CONFIRMED" : "réplica déjà à jour")
                            + (created ? ", livraison WAITING créée" : ", livraison déjà existante");
                }
                case "StockRejected" -> repo.updateReplica(eventId, orderId, "orderStatus", "REJECTED", at)
                        ? "réplica REJECTED (pas de livraison)" : "déjà appliqué, ignoré";
                case "DeliveryAddressChanged" -> repo.updateReplica(eventId, orderId, "deliveryAddress",
                        p.get("deliveryAddress").asText(), at) ? "adresse du réplica mise à jour" : "déjà appliqué, ignoré";
                default -> "type non géré, ignoré";
            };
            log.info("{} {} {} -> {}", eventId, type, orderId, result);

            // Acquittement SEULEMENT après les écritures dans MongoDB
            redis.opsForList().remove(PROCESSING, 1, raw);
        } catch (Exception ex) {
            log.error("Échec du traitement, événement conservé dans {}", PROCESSING, ex);
        }
    }
}
