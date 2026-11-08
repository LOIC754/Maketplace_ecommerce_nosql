package com.marketplace.order.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Agrégat Commande : un document de la collection order_db.orders.
// Même structure que les documents chargés au niveau 1.
@Document(collection = "orders")
public class Order {

    @Id
    private String id;                                   // _id technique généré par MongoDB

    @Indexed(unique = true, name = "uk_orderId")         // racine de l'agrégat, unique
    private String orderId;

    private String customerId;
    private List<OrderLine> lines;
    private double total;
    private String deliveryAddress;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<String> appliedEventIds = new ArrayList<>();   // idempotence (inbox)

    protected Order() {
        // utilisé par Spring Data pour relire un document
    }

    // Création d'une commande : le total est CALCULÉ ici (invariant métier),
    // jamais fourni par le client.
    public static Order place(String orderId, String customerId, List<OrderLine> lines, String deliveryAddress) {
        Order o = new Order();
        o.orderId = orderId;
        o.customerId = customerId;
        o.lines = List.copyOf(lines);
        o.total = lines.stream().mapToDouble(OrderLine::amount).sum();
        o.deliveryAddress = deliveryAddress;
        o.status = "PENDING";
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public List<OrderLine> getLines() { return lines; }
    public double getTotal() { return total; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<String> getAppliedEventIds() { return appliedEventIds; }
}
