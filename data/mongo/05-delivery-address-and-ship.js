// =====================================================================
// Service Livraison (base delivery_db)
// 1. Consomme DeliveryAddressChanged_v1 (evt-0009) : MISE À JOUR du réplica de CMD-3
// 2. Exécute la commande ShipOrder (W5) pour CMD-1 : LIV-1 passe à SHIPPED
// =====================================================================
db = db.getSiblingDB("delivery_db");

function log(label, r) {
  print(label + " -> trouvé: " + r.matchedCount + ", modifié: " + r.modifiedCount);
}

// ---------------------------------------------------------------------
// 1. DeliveryAddressChanged_v1 : nouvelle adresse dans le réplica
// ---------------------------------------------------------------------
log("evt-0009 DeliveryAddressChanged CMD-3", db.order_replicas.updateOne(
  { orderId: "CMD-3", appliedEventIds: { $ne: "evt-0009" } },
  { $set: { deliveryAddress: "20 rue Oberkampf, 75011 Paris", lastEventId: "evt-0009",
            updatedAt: new Date("2026-10-29T12:00:00Z") },
    $push: { appliedEventIds: "evt-0009" } }));

// ---------------------------------------------------------------------
// 2. ShipOrder (W5) pour CMD-1
//    Autonomie : Livraison vérifie le statut de la commande dans SON réplica,
//    sans interroger le service Commande.
// ---------------------------------------------------------------------
const replica = db.order_replicas.findOne({ orderId: "CMD-1" });
print("Réplica CMD-1 : statut " + replica.orderStatus + ", adresse " + replica.deliveryAddress);

// La livraison passe de WAITING à SHIPPED (le filtre interdit une double expédition)
log("W5 ShipOrder CMD-1 (LIV-1)", db.deliveries.updateOne(
  { orderId: "CMD-1", status: "WAITING" },
  { $set: { status: "SHIPPED", carrier: "Colissimo", trackingNumber: "6A12345678901",
            shippedAt: new Date("2026-10-29T14:00:00Z") } }));

// Contrôle
db.deliveries.find({}, { _id: 0, deliveryId: 1, orderId: 1, status: 1, carrier: 1 }).sort({ deliveryId: 1 })
  .forEach(d => print(d.deliveryId + " | " + d.orderId + " | " + d.status + " | " + d.carrier));
