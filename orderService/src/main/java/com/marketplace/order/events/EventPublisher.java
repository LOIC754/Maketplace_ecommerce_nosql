package com.marketplace.order.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Publication des événements du service Commande dans la file d'entrée Redis
@Component
public class EventPublisher {

    public static final String PUBLISHED = "shop:events:published";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public EventPublisher(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    // Construit l'enveloppe commune (étape 4) et fait RPUSH shop:events:published.
    // Retourne l'eventId généré.
    public String publish(String eventType, String aggregateId, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt-" + UUID.randomUUID());   // identifiant unique (idempotence)
        event.put("eventType", eventType);
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("emitter", "Commande");
        event.put("aggregateId", aggregateId);
        event.put("payload", payload);
        try {
            redis.opsForList().rightPush(PUBLISHED, json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation impossible de l'événement " + eventType, e);
        }
        return (String) event.get("eventId");
    }
}
