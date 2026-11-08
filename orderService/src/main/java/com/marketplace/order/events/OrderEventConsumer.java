package com.marketplace.order.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.order.domain.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Consommation des événements destinés au service Commande :
// StockReserved_v1, StockRejected_v1, OrderShipped_v1
@Component
public class OrderEventConsumer {

    static final String READY = "shop:order:ready";
    static final String PROCESSING = "shop:order:processing";

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final OrderService orders;

    public OrderEventConsumer(StringRedisTemplate redis, ObjectMapper json, OrderService orders) {
        this.redis = redis;
        this.json = json;
        this.orders = orders;
    }

    // Reprise après panne : au démarrage, les événements restés dans processing
    // (service arrêté avant l'acquittement) sont remis en tête de ready.
    // @PostConstruct s'exécute AVANT le lancement de la lecture périodique :
    // la reprise et la consommation ne peuvent pas se chevaucher.
    @PostConstruct
    public void recover() {
        int n = 0;
        while (redis.opsForList().move(PROCESSING, Direction.last(), READY, Direction.first()) != null) n++;
        if (n > 0) log.info("Reprise : {} événement(s) remis dans {}", n, READY);
    }

    // Lecture périodique de la file : un événement à la fois
    @Scheduled(fixedDelayString = "${order.consumer.poll-delay-ms:500}")
    public void consumeOne() {
        // LMOVE shop:order:ready shop:order:processing LEFT RIGHT (atomique)
        String raw = redis.opsForList().move(READY, Direction.first(), PROCESSING, Direction.last());
        if (raw == null) return;                                   // file vide

        try {
            JsonNode e = json.readTree(raw);
            String eventId = e.get("eventId").asText();
            String type = e.get("eventType").asText();
            String orderId = e.get("payload").get("orderId").asText();

            String newStatus = switch (type) {
                case "StockReserved" -> "CONFIRMED";
                case "StockRejected" -> "REJECTED";
                case "OrderShipped" -> "SHIPPED";
                default -> null;                                    // événement non concerné
            };
            if (newStatus == null) {
                log.warn("{} ignoré : type {} non géré", eventId, type);
            } else {
                boolean applied = orders.applyStatus(orderId, eventId, newStatus);
                log.info("{} {} {} -> {}", eventId, type, orderId, applied ? newStatus : "déjà appliqué, ignoré");
            }
            // Acquittement SEULEMENT après l'écriture dans MongoDB
            redis.opsForList().remove(PROCESSING, 1, raw);
        } catch (Exception ex) {
            // Pas d'acquittement : l'événement reste dans processing et sera repris au redémarrage
            log.error("Échec du traitement, événement conservé dans {}", PROCESSING, ex);
        }
    }
}
