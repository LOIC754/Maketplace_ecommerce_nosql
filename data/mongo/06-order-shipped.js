// =====================================================================
// Service Commande (base order_db)
// Consomme OrderShipped_v1 (evt-0010) : CMD-1 passe à SHIPPED
// =====================================================================
db = db.getSiblingDB("order_db");

const r = db.orders.updateOne(
  { orderId: "CMD-1", appliedEventIds: { $ne: "evt-0010" } },
  { $set: { status: "SHIPPED", updatedAt: new Date("2026-10-29T14:00:00Z") },
    $push: { appliedEventIds: "evt-0010" } });
print("evt-0010 OrderShipped CMD-1 -> trouvé: " + r.matchedCount + ", modifié: " + r.modifiedCount);

// Contrôle
db.orders.find({}, { _id: 0, orderId: 1, status: 1 }).sort({ orderId: 1 })
  .forEach(o => print(o.orderId + " | " + o.status));
