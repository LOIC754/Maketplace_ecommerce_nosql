package com.marketplace.query.api;

import com.marketplace.query.domain.OrderViewRepository;
import com.marketplace.query.events.ProjectionUpdater;
import com.marketplace.query.events.QueryEventConsumer;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// API REST du service Query : lecture seule pour l'écran « Mes commandes »
@RestController
public class OrderViewController {

    private static final Logger log = LoggerFactory.getLogger(OrderViewController.class);
    static final String ARCHIVE = "shop:events:archive";

    private final OrderViewRepository views;
    private final ProjectionUpdater projector;
    private final QueryEventConsumer consumer;
    private final StringRedisTemplate redis;

    public OrderViewController(OrderViewRepository views, ProjectionUpdater projector,
                               QueryEventConsumer consumer, StringRedisTemplate redis) {
        this.views = views;
        this.projector = projector;
        this.consumer = consumer;
        this.redis = redis;
    }

    // R4 : GET /customers/{customerId}/orders -> « Mes commandes », les plus récentes d'abord
    @GetMapping("/customers/{customerId}/orders")
    public List<Document> myOrders(@PathVariable String customerId) {
        return views.byCustomer(customerId);
    }

    // Détail : GET /order-views/{orderId} -> suivi complet avec la timeline
    @GetMapping("/order-views/{orderId}")
    public ResponseEntity<Document> detail(@PathVariable String orderId) {
        Document v = views.byOrder(orderId);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
    }

    // Reconstruction (replay) : POST /order-views/rebuild
    // La projection est vidée puis reconstruite en rejouant tout le journal
    // shop:events:archive. Elle ne dépend que des événements : on peut la jeter.
    @PostMapping("/order-views/rebuild")
    public synchronized Map<String, Object> rebuild() {
        consumer.pause(true);                          // pas de consommation pendant la reconstruction
        try {
            long deleted = views.deleteAll();
            List<String> events = redis.opsForList().range(ARCHIVE, 0, -1);
            int replayed = 0;
            for (String raw : events) {
                try {
                    projector.apply(raw);
                    replayed++;
                } catch (Exception e) {
                    log.warn("Événement de l'archive ignoré pendant la reconstruction", e);
                }
            }
            log.info("Reconstruction : {} vue(s) supprimée(s), {} événement(s) rejoué(s)", deleted, replayed);
            return Map.of("deletedViews", deleted, "replayedEvents", replayed);
        } finally {
            consumer.pause(false);
        }
    }
}
