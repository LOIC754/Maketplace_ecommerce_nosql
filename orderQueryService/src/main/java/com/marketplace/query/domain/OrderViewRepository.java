package com.marketplace.query.domain;

import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.query.Criteria.where;

// Projection order_views (base query_db) : une vue dénormalisée par commande,
// même forme qu'au niveau 1. Toutes les écritures sont idempotentes.
@Repository
public class OrderViewRepository {

    static final String VIEWS = "order_views";

    // Statut technique -> texte lisible pour le client
    private static final Map<String, String> LABELS = Map.of(
            "PENDING", "En attente", "CONFIRMED", "Confirmée",
            "REJECTED", "Refusée", "SHIPPED", "Expédiée");

    private final MongoTemplate mongo;

    public OrderViewRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // Index des motifs d'accès (mêmes noms que data/mongo/02-indexes.js)
    @PostConstruct
    public void ensureIndexes() {
        mongo.indexOps(VIEWS).ensureIndex(new Index().on("orderId", Sort.Direction.ASC).unique().named("uk_orderId"));
        mongo.indexOps(VIEWS).ensureIndex(new Index().on("customerId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC).named("idx_customerId_createdAt"));
    }

    // OrderCreated : création de la vue (upsert + $setOnInsert)
    // Transformations : itemsCount calculé, prix unitaires retirés, statut traduit
    public boolean create(String eventId, String orderId, String customerId, Instant createdAt,
                          double total, List<Document> items, String address) {
        int itemsCount = items.stream().mapToInt(i -> i.getInteger("quantity")).sum();
        Update u = new Update()
                .setOnInsert("orderId", orderId)
                .setOnInsert("customerId", customerId)
                .setOnInsert("createdAt", Date.from(createdAt))
                .setOnInsert("total", total)
                .setOnInsert("itemsCount", itemsCount)
                .setOnInsert("items", items)
                .setOnInsert("deliveryAddress", address)
                .setOnInsert("status", "PENDING")
                .setOnInsert("statusLabel", LABELS.get("PENDING"))
                .setOnInsert("carrier", null)
                .setOnInsert("trackingNumber", null)
                .setOnInsert("timeline", List.of(step("PENDING", "Commande reçue", createdAt)))
                .setOnInsert("appliedEventIds", List.of(eventId))
                .setOnInsert("updatedAt", Date.from(createdAt));
        return mongo.upsert(Query.query(where("orderId").is(orderId)), u, VIEWS).getUpsertedId() != null;
    }

    // Changement de statut : nouveau statut + nouvelle étape dans la timeline
    // extra : champs supplémentaires (transporteur, numéro de suivi)
    public boolean changeStatus(String eventId, String orderId, String status, String stepLabel,
                                Instant at, Map<String, Object> extra) {
        Update u = new Update().set("status", status).set("statusLabel", LABELS.get(status))
                .set("updatedAt", Date.from(at))
                .push("timeline", step(status, stepLabel, at))
                .push("appliedEventIds", eventId);
        extra.forEach(u::set);
        return apply(eventId, orderId, u);
    }

    // DeliveryAddressChanged : nouvelle adresse, sans étape de timeline
    public boolean changeAddress(String eventId, String orderId, String address, Instant at) {
        Update u = new Update().set("deliveryAddress", address).set("updatedAt", Date.from(at))
                .push("appliedEventIds", eventId);
        return apply(eventId, orderId, u);
    }

    // Écriture filtrée par $ne : un événement déjà appliqué ne modifie rien
    private boolean apply(String eventId, String orderId, Update u) {
        Query q = Query.query(where("orderId").is(orderId).and("appliedEventIds").ne(eventId));
        return mongo.updateFirst(q, u, VIEWS).getModifiedCount() == 1;
    }

    private Document step(String status, String label, Instant at) {
        return new Document("status", status).append("label", label).append("at", Date.from(at));
    }

    // R4 : commandes d'un client, les plus récentes d'abord (index idx_customerId_createdAt)
    public List<Document> byCustomer(String customerId) {
        Query q = Query.query(where("customerId").is(customerId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(20);
        q.fields().exclude("_id").include("orderId", "createdAt", "total", "itemsCount", "statusLabel");
        return mongo.find(q, Document.class, VIEWS);
    }

    // Détail d'une commande (suivi complet avec la timeline)
    public Document byOrder(String orderId) {
        Query q = Query.query(where("orderId").is(orderId));
        q.fields().exclude("_id", "appliedEventIds");
        return mongo.findOne(q, Document.class, VIEWS);
    }

    // Reconstruction : on vide la projection (les index sont conservés)
    public long deleteAll() {
        return mongo.remove(new Query(), VIEWS).getDeletedCount();
    }
}
