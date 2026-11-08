package com.marketplace.delivery.domain;

import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

// Accès aux deux collections du service Livraison (base delivery_db) :
// - order_replicas : réplica de la Commande, en lecture seule, mis à jour par événements
// - deliveries     : agrégat Livraison
// Les documents ont exactement la même forme qu'au niveau 1.
@Repository
public class DeliveryRepository {

    static final String REPLICAS = "order_replicas";
    static final String DELIVERIES = "deliveries";

    private final MongoTemplate mongo;

    public DeliveryRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // Index des motifs d'accès (mêmes noms que data/mongo/02-indexes.js)
    @PostConstruct
    public void ensureIndexes() {
        mongo.indexOps(REPLICAS).ensureIndex(new Index().on("orderId", Sort.Direction.ASC).unique().named("uk_orderId"));
        mongo.indexOps(DELIVERIES).ensureIndex(new Index().on("orderId", Sort.Direction.ASC).unique().named("uk_orderId"));
        mongo.indexOps(DELIVERIES).ensureIndex(new Index().on("status", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.ASC).named("idx_status_createdAt"));
    }

    // ------------------------------------------------------------------
    // Réplica : création et mises à jour, toutes idempotentes
    // ------------------------------------------------------------------

    // OrderCreated : création du réplica (upsert + $setOnInsert)
    // Retourne true si le réplica vient d'être créé
    public boolean createReplica(String eventId, String orderId, String address, List<Document> items, Instant at) {
        Update u = new Update()
                .setOnInsert("orderId", orderId)
                .setOnInsert("deliveryAddress", address)
                .setOnInsert("items", items)
                .setOnInsert("orderStatus", "PENDING")
                .setOnInsert("lastEventId", eventId)
                .setOnInsert("appliedEventIds", List.of(eventId))
                .setOnInsert("updatedAt", Date.from(at));
        UpdateResult r = mongo.upsert(Query.query(where("orderId").is(orderId)), u, REPLICAS);
        return r.getUpsertedId() != null;
    }

    // Mise à jour d'un champ du réplica, ignorée si l'événement est déjà appliqué
    // Retourne true si le réplica a été modifié
    public boolean updateReplica(String eventId, String orderId, String field, Object value, Instant at) {
        Query q = Query.query(where("orderId").is(orderId).and("appliedEventIds").ne(eventId));
        Update u = new Update().set(field, value).set("lastEventId", eventId)
                .set("updatedAt", Date.from(at)).push("appliedEventIds", eventId);
        return mongo.updateFirst(q, u, REPLICAS).getModifiedCount() == 1;
    }

    public Document findReplica(String orderId) {
        return mongo.findOne(Query.query(where("orderId").is(orderId)), Document.class, REPLICAS);
    }

    // ------------------------------------------------------------------
    // Agrégat Livraison
    // ------------------------------------------------------------------

    // StockReserved : création de la livraison WAITING (une seule par commande)
    // Retourne true si la livraison vient d'être créée
    public boolean createDelivery(String orderId, Instant at) {
        Update u = new Update()
                .setOnInsert("deliveryId", "LIV-" + orderId.replace("CMD-", ""))
                .setOnInsert("orderId", orderId)
                .setOnInsert("carrier", null)
                .setOnInsert("trackingNumber", null)
                .setOnInsert("status", "WAITING")
                .setOnInsert("createdAt", Date.from(at))
                .setOnInsert("shippedAt", null);
        return mongo.upsert(Query.query(where("orderId").is(orderId)), u, DELIVERIES).getUpsertedId() != null;
    }

    // W5 : WAITING -> SHIPPED ; le filtre interdit une double expédition
    // Retourne true si la livraison a été expédiée
    public boolean ship(String orderId, String carrier, String trackingNumber) {
        Query q = Query.query(where("orderId").is(orderId).and("status").is("WAITING"));
        Update u = new Update().set("status", "SHIPPED").set("carrier", carrier)
                .set("trackingNumber", trackingNumber).set("shippedAt", new Date());
        return mongo.updateFirst(q, u, DELIVERIES).getModifiedCount() == 1;
    }

    public Document findDelivery(String orderId) {
        return mongo.findOne(Query.query(where("orderId").is(orderId)), Document.class, DELIVERIES);
    }

    // R3 : livraisons à préparer, les plus anciennes d'abord, avec l'adresse et
    // les articles du réplica (jointure LOCALE : les deux collections sont à Livraison)
    public List<Document> toPrepare() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("status", "WAITING")),
                new Document("$sort", new Document("createdAt", 1)),
                new Document("$lookup", new Document("from", REPLICAS).append("localField", "orderId")
                        .append("foreignField", "orderId").append("as", "order")),
                new Document("$unwind", "$order"),
                new Document("$project", new Document("_id", 0).append("deliveryId", 1).append("orderId", 1)
                        .append("createdAt", 1).append("deliveryAddress", "$order.deliveryAddress")
                        .append("items", "$order.items")));
        return mongo.getCollection(DELIVERIES).aggregate(pipeline).into(new ArrayList<>());
    }
}
