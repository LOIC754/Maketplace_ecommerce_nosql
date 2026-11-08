package com.marketplace.stock.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.stock.domain.StockRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Consommation de OrderCreated_v1 : réservation atomique du stock (script Lua)
// et publication de StockReserved_v1 ou StockRejected_v1
@Component
public class StockEventConsumer {

    static final String READY = "shop:stock:ready";
    static final String PROCESSING = "shop:stock:processing";
    static final String PROCESSED = "shop:stock:processed";
    static final String PUBLISHED = "shop:events:published";

    private static final Logger log = LoggerFactory.getLogger(StockEventConsumer.class);

    // Script Lua chargé depuis src/main/resources/scripts/reserve.lua
    private final RedisScript<Long> reserveScript;

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public StockEventConsumer(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/reserve.lua"));
        script.setResultType(Long.class);
        this.reserveScript = script;
    }

    // Reprise après panne, AVANT le lancement de la lecture périodique
    @PostConstruct
    public void recover() {
        int n = 0;
        while (redis.opsForList().move(PROCESSING, Direction.last(), READY, Direction.first()) != null) n++;
        if (n > 0) log.info("Reprise : {} événement(s) remis dans {}", n, READY);
    }

    @Scheduled(fixedDelayString = "${stock.consumer.poll-delay-ms:500}")
    public void consumeOne() {
        // LMOVE shop:stock:ready shop:stock:processing LEFT RIGHT (atomique)
        String raw = redis.opsForList().move(READY, Direction.first(), PROCESSING, Direction.last());
        if (raw == null) return;

        try {
            JsonNode e = json.readTree(raw);
            String eventId = e.get("eventId").asText();
            if (!"OrderCreated".equals(e.get("eventType").asText())) {
                log.warn("{} ignoré : type {} non géré", eventId, e.get("eventType").asText());
            } else {
                reserve(eventId, e.get("payload"));
            }
            // Acquittement : la réponse est déjà publiée par le script Lua
            redis.opsForList().remove(PROCESSING, 1, raw);
        } catch (Exception ex) {
            log.error("Échec du traitement, événement conservé dans {}", PROCESSING, ex);
        }
    }

    // Prépare les clés et arguments du script Lua, puis l'exécute
    private void reserve(String eventId, JsonNode order) throws Exception {
        String orderId = order.get("orderId").asText();
        String now = Instant.now().toString();

        List<String> keys = new ArrayList<>(List.of(PROCESSED, PUBLISHED));
        List<String> quantities = new ArrayList<>();
        List<Map<String, Object>> reservedLines = new ArrayList<>();
        for (JsonNode line : order.get("lines")) {
            String productId = line.get("productId").asText();
            int qty = line.get("quantity").asInt();
            keys.add(StockRepository.key(productId));
            quantities.add(String.valueOf(qty));
            reservedLines.add(Map.of("productId", productId, "quantity", qty));
        }

        // Les deux réponses possibles sont préparées ; le script publie la bonne
        String reserved = json.writeValueAsString(envelope("StockReserved", orderId, now,
                Map.of("orderId", orderId, "reservedLines", reservedLines)));
        String rejected = json.writeValueAsString(envelope("StockRejected", orderId, now,
                Map.of("orderId", orderId, "reason", "INSUFFICIENT_STOCK")));

        List<String> args = new ArrayList<>(List.of(eventId, now, reserved, rejected));
        args.addAll(quantities);

        Long result = redis.execute(reserveScript, keys, args.toArray());
        String outcome = switch (result.intValue()) {
            case 1 -> "stock réservé, StockReserved publié";
            case 0 -> "stock insuffisant, StockRejected publié";
            default -> "déjà traité, ignoré";
        };
        log.info("{} OrderCreated {} -> {}", eventId, orderId, outcome);
    }

    // Enveloppe commune des événements (étape 4)
    private Map<String, Object> envelope(String type, String orderId, String now, Map<String, Object> payload) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("eventId", "evt-" + UUID.randomUUID());
        e.put("eventType", type);
        e.put("eventVersion", 1);
        e.put("occurredAt", now);
        e.put("emitter", "Stock");
        e.put("aggregateId", orderId);
        e.put("payload", payload);
        return e;
    }
}
