package com.marketplace.query.events;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Consommation des événements destinés au service Query (les 5 types)
@Component
public class QueryEventConsumer {

    static final String READY = "shop:query:ready";
    static final String PROCESSING = "shop:query:processing";

    private static final Logger log = LoggerFactory.getLogger(QueryEventConsumer.class);

    private final StringRedisTemplate redis;
    private final ProjectionUpdater projector;

    // Pendant une reconstruction, la consommation normale est suspendue
    private volatile boolean paused = false;

    public QueryEventConsumer(StringRedisTemplate redis, ProjectionUpdater projector) {
        this.redis = redis;
        this.projector = projector;
    }

    // Reprise après panne, AVANT le lancement de la lecture périodique
    @PostConstruct
    public void recover() {
        int n = 0;
        while (redis.opsForList().move(PROCESSING, Direction.last(), READY, Direction.first()) != null) n++;
        if (n > 0) log.info("Reprise : {} événement(s) remis dans {}", n, READY);
    }

    public void pause(boolean value) {
        this.paused = value;
    }

    @Scheduled(fixedDelayString = "${query.consumer.poll-delay-ms:500}")
    public synchronized void consumeOne() {
        if (paused) return;
        // LMOVE shop:query:ready shop:query:processing LEFT RIGHT (atomique)
        String raw = redis.opsForList().move(READY, Direction.first(), PROCESSING, Direction.last());
        if (raw == null) return;
        try {
            log.info(projector.apply(raw));
            // Acquittement SEULEMENT après l'écriture dans MongoDB
            redis.opsForList().remove(PROCESSING, 1, raw);
        } catch (Exception ex) {
            log.error("Échec du traitement, événement conservé dans {}", PROCESSING, ex);
        }
    }
}
