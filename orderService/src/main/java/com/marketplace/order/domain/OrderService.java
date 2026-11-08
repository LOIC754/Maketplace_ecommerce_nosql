package com.marketplace.order.domain;

import com.marketplace.order.events.EventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

// Logique métier du service Commande
@Service
public class OrderService {

    // Résultats possibles d'une commande reçue
    public enum Outcome { ACCEPTED, ALREADY_EXISTS, NOT_FOUND, FORBIDDEN }

    public record Result(Outcome outcome, String eventId) {}

    private final MongoTemplate mongo;
    private final EventPublisher publisher;

    public OrderService(MongoTemplate mongo, EventPublisher publisher) {
        this.mongo = mongo;
        this.publisher = publisher;
    }

    // W1 : PlaceOrder -> agrégat PENDING (transaction locale) puis OrderCreated_v1
    public Result placeOrder(String orderId, String customerId, List<OrderLine> lines, String address) {
        Order order = Order.place(orderId, customerId, lines, address);
        try {
            mongo.insert(order);                         // l'index unique refuse un doublon
        } catch (DuplicateKeyException e) {
            return new Result(Outcome.ALREADY_EXISTS, null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("customerId", order.getCustomerId());
        payload.put("lines", order.getLines());
        payload.put("total", order.getTotal());
        payload.put("deliveryAddress", order.getDeliveryAddress());
        payload.put("status", order.getStatus());
        payload.put("createdAt", order.getCreatedAt().toString());
        return new Result(Outcome.ACCEPTED, publisher.publish("OrderCreated", orderId, payload));
    }

    // W2 : ChangeDeliveryAddress -> mise à jour puis DeliveryAddressChanged_v1
    // Règle métier dans le filtre : interdit si la commande est expédiée ou refusée
    public Result changeAddress(String orderId, String address) {
        if (!mongo.exists(Query.query(where("orderId").is(orderId)), Order.class)) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        Query q = Query.query(where("orderId").is(orderId).and("status").in("PENDING", "CONFIRMED"));
        Update u = new Update().set("deliveryAddress", address).set("updatedAt", Instant.now());
        if (mongo.updateFirst(q, u, Order.class).getMatchedCount() == 0) {
            return new Result(Outcome.FORBIDDEN, null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("deliveryAddress", address);
        return new Result(Outcome.ACCEPTED, publisher.publish("DeliveryAddressChanged", orderId, payload));
    }

    // R1 : lecture d'une commande par son identifiant
    public Optional<Order> find(String orderId) {
        return Optional.ofNullable(mongo.findOne(Query.query(where("orderId").is(orderId)), Order.class));
    }

    // Application d'un événement reçu : nouveau statut, en une seule écriture atomique.
    // Le filtre $ne ignore un événement déjà appliqué (idempotence).
    // Retourne true si la commande a été modifiée.
    public boolean applyStatus(String orderId, String eventId, String newStatus) {
        Criteria c = where("orderId").is(orderId).and("appliedEventIds").ne(eventId);
        Update u = new Update().set("status", newStatus).set("updatedAt", Instant.now()).push("appliedEventIds", eventId);
        return mongo.updateFirst(Query.query(c), u, Order.class).getModifiedCount() == 1;
    }
}
