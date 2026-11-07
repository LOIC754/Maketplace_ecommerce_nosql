// =====================================================================
// Service Commande (base order_db)
// 1. Consomme les 4 réponses du Stock (evt-0005 à evt-0008) : CONFIRMED / REJECTED
// 2. Exécute la commande ChangeDeliveryAddress (W2) pour CMD-3
// Idempotence : l'eventId est enregistré dans la commande (appliedEventIds),
// dans la même écriture atomique que le changement de statut.
// =====================================================================
db = db.getSiblingDB("order_db");

// Affiche le résultat d'une écriture : 1 = appliquée, 0 = ignorée
function log(label, r) {
  print(label + " -> trouvé: " + r.matchedCount + ", modifié: " + r.modifiedCount);
}

// ---------------------------------------------------------------------
// 1. Réponses du Stock : mise à jour du statut de la commande
//    Le filtre $ne ignore un événement déjà appliqué.
// ---------------------------------------------------------------------
log("evt-0005 StockReserved CMD-1", db.orders.updateOne(
  { orderId: "CMD-1", appliedEventIds: { $ne: "evt-0005" } },
  { $set: { status: "CONFIRMED", updatedAt: new Date("2026-10-29T10:00:02Z") },
    $push: { appliedEventIds: "evt-0005" } }));

log("evt-0006 StockRejected CMD-2", db.orders.updateOne(
  { orderId: "CMD-2", appliedEventIds: { $ne: "evt-0006" } },
  { $set: { status: "REJECTED", updatedAt: new Date("2026-10-29T10:10:02Z") },
    $push: { appliedEventIds: "evt-0006" } }));

log("evt-0007 StockReserved CMD-3", db.orders.updateOne(
  { orderId: "CMD-3", appliedEventIds: { $ne: "evt-0007" } },
  { $set: { status: "CONFIRMED", updatedAt: new Date("2026-10-29T11:00:02Z") },
    $push: { appliedEventIds: "evt-0007" } }));

log("evt-0008 StockReserved CMD-4", db.orders.updateOne(
  { orderId: "CMD-4", appliedEventIds: { $ne: "evt-0008" } },
  { $set: { status: "CONFIRMED", updatedAt: new Date("2026-10-29T11:30:02Z") },
    $push: { appliedEventIds: "evt-0008" } }));

// ---------------------------------------------------------------------
// 2. Commande ChangeDeliveryAddress (W2) pour CMD-3
//    Règle métier : l'adresse ne peut plus changer une fois la commande expédiée
//    ou refusée. Le filtre sur le statut fait respecter cette règle.
// ---------------------------------------------------------------------
log("W2 ChangeDeliveryAddress CMD-3", db.orders.updateOne(
  { orderId: "CMD-3", status: { $in: ["PENDING", "CONFIRMED"] } },
  { $set: { deliveryAddress: "20 rue Oberkampf, 75011 Paris", updatedAt: new Date("2026-10-29T12:00:00Z") } }));

// Contrôle
db.orders.find({}, { _id: 0, orderId: 1, status: 1, deliveryAddress: 1 }).sort({ orderId: 1 })
  .forEach(o => print(o.orderId + " | " + o.status + " | " + o.deliveryAddress));
