// =====================================================================
// Service Livraison : consommation des 8 événements de shop:delivery:ready
// Base delivery_db : réplica de la Commande (order_replicas) + livraisons (deliveries)
// Idempotence : l'eventId est enregistré DANS le document qu'il modifie
// (appliedEventIds), donc dans la même écriture atomique que la donnée.
// =====================================================================
db = db.getSiblingDB("delivery_db");

// Script rejouable : on vide les collections sans les supprimer (les index sont conservés)
db.order_replicas.deleteMany({});
db.deliveries.deleteMany({});

// Affiche le résultat d'une écriture : 1 = appliquée, 0 = ignorée (déjà traitée)
function log(label, r) {
  print(label + " -> créé: " + r.upsertedCount + ", modifié: " + r.modifiedCount);
}

// ---------------------------------------------------------------------
// 1. OrderCreated_v1 : CRÉATION du réplica (upsert + $setOnInsert)
//    Si le réplica existe déjà, rien n'est modifié : l'événement est ignoré.
// ---------------------------------------------------------------------
log("evt-0001 OrderCreated CMD-1", db.order_replicas.updateOne(
  { orderId: "CMD-1" },
  { $setOnInsert: {
      orderId: "CMD-1",
      deliveryAddress: "8 rue Lafayette, 75009 Paris",
      items: [
        { productId: "PROD-1", productName: "Baskets urbaines", quantity: 2 },
        { productId: "PROD-2", productName: "Casque audio", quantity: 1 }
      ],
      orderStatus: "PENDING",
      lastEventId: "evt-0001",
      appliedEventIds: ["evt-0001"],
      updatedAt: new Date("2026-10-29T10:00:00Z") } },
  { upsert: true }));

log("evt-0002 OrderCreated CMD-2", db.order_replicas.updateOne(
  { orderId: "CMD-2" },
  { $setOnInsert: {
      orderId: "CMD-2",
      deliveryAddress: "5 avenue Foch, 75016 Paris",
      items: [ { productId: "PROD-1", productName: "Baskets urbaines", quantity: 10 } ],
      orderStatus: "PENDING",
      lastEventId: "evt-0002",
      appliedEventIds: ["evt-0002"],
      updatedAt: new Date("2026-10-29T10:10:00Z") } },
  { upsert: true }));

log("evt-0003 OrderCreated CMD-3", db.order_replicas.updateOne(
  { orderId: "CMD-3" },
  { $setOnInsert: {
      orderId: "CMD-3",
      deliveryAddress: "8 rue Lafayette, 75009 Paris",
      items: [ { productId: "PROD-5", productName: "Gourde isotherme", quantity: 3 } ],
      orderStatus: "PENDING",
      lastEventId: "evt-0003",
      appliedEventIds: ["evt-0003"],
      updatedAt: new Date("2026-10-29T11:00:00Z") } },
  { upsert: true }));

log("evt-0004 OrderCreated CMD-4", db.order_replicas.updateOne(
  { orderId: "CMD-4" },
  { $setOnInsert: {
      orderId: "CMD-4",
      deliveryAddress: "12 rue de Rivoli, 75004 Paris",
      items: [ { productId: "PROD-4", productName: "Montre connectee", quantity: 1 } ],
      orderStatus: "PENDING",
      lastEventId: "evt-0004",
      appliedEventIds: ["evt-0004"],
      updatedAt: new Date("2026-10-29T11:30:00Z") } },
  { upsert: true }));

// ---------------------------------------------------------------------
// 2. StockReserved_v1 / StockRejected_v1 : MISE À JOUR du réplica
//    Le filtre { appliedEventIds: { $ne: <eventId> } } ne sélectionne le réplica
//    que si l'événement n'y a pas encore été appliqué.
// ---------------------------------------------------------------------
log("evt-0005 StockReserved CMD-1", db.order_replicas.updateOne(
  { orderId: "CMD-1", appliedEventIds: { $ne: "evt-0005" } },
  { $set: { orderStatus: "CONFIRMED", lastEventId: "evt-0005", updatedAt: new Date("2026-10-29T10:00:02Z") },
    $push: { appliedEventIds: "evt-0005" } }));

log("evt-0006 StockRejected CMD-2", db.order_replicas.updateOne(
  { orderId: "CMD-2", appliedEventIds: { $ne: "evt-0006" } },
  { $set: { orderStatus: "REJECTED", lastEventId: "evt-0006", updatedAt: new Date("2026-10-29T10:10:02Z") },
    $push: { appliedEventIds: "evt-0006" } }));

log("evt-0007 StockReserved CMD-3", db.order_replicas.updateOne(
  { orderId: "CMD-3", appliedEventIds: { $ne: "evt-0007" } },
  { $set: { orderStatus: "CONFIRMED", lastEventId: "evt-0007", updatedAt: new Date("2026-10-29T11:00:02Z") },
    $push: { appliedEventIds: "evt-0007" } }));

log("evt-0008 StockReserved CMD-4", db.order_replicas.updateOne(
  { orderId: "CMD-4", appliedEventIds: { $ne: "evt-0008" } },
  { $set: { orderStatus: "CONFIRMED", lastEventId: "evt-0008", updatedAt: new Date("2026-10-29T11:30:02Z") },
    $push: { appliedEventIds: "evt-0008" } }));

// ---------------------------------------------------------------------
// 3. StockReserved_v1 : CRÉATION de la livraison WAITING (agrégat Livraison)
//    Uniquement pour les commandes confirmées : pas de livraison pour CMD-2.
//    L'index unique sur orderId garantit une seule livraison par commande.
// ---------------------------------------------------------------------
log("evt-0005 -> livraison LIV-1", db.deliveries.updateOne(
  { orderId: "CMD-1" },
  { $setOnInsert: { deliveryId: "LIV-1", orderId: "CMD-1", carrier: null, trackingNumber: null,
                    status: "WAITING", createdAt: new Date("2026-10-29T10:00:02Z"), shippedAt: null } },
  { upsert: true }));

log("evt-0007 -> livraison LIV-3", db.deliveries.updateOne(
  { orderId: "CMD-3" },
  { $setOnInsert: { deliveryId: "LIV-3", orderId: "CMD-3", carrier: null, trackingNumber: null,
                    status: "WAITING", createdAt: new Date("2026-10-29T11:00:02Z"), shippedAt: null } },
  { upsert: true }));

log("evt-0008 -> livraison LIV-4", db.deliveries.updateOne(
  { orderId: "CMD-4" },
  { $setOnInsert: { deliveryId: "LIV-4", orderId: "CMD-4", carrier: null, trackingNumber: null,
                    status: "WAITING", createdAt: new Date("2026-10-29T11:30:02Z"), shippedAt: null } },
  { upsert: true }));

print("Réplicas : " + db.order_replicas.countDocuments() + " | Livraisons : " + db.deliveries.countDocuments());
