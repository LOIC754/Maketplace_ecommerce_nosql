// =====================================================================
// Service Query / Suivi client (base query_db)
// Consomme ses 10 événements et construit la projection order_views (CQRS) :
// une vue dénormalisée par commande, prête pour l'écran « Mes commandes » (R4).
// Idempotence : l'eventId est enregistré dans la vue (appliedEventIds),
// dans la même écriture atomique que la transformation.
// =====================================================================
db = db.getSiblingDB("query_db");

// Script rejouable : on vide la collection en conservant ses index
db.order_views.deleteMany({});

function log(label, r) {
  print(label + " -> créé: " + (r.upsertedCount || 0) + ", modifié: " + r.modifiedCount);
}

// ---------------------------------------------------------------------
// 1. OrderCreated_v1 : création de la vue
//    Transformations : itemsCount calculé, prix unitaires retirés,
//    statut technique traduit (statusLabel), début de la timeline.
// ---------------------------------------------------------------------
log("evt-0001 OrderCreated CMD-1", db.order_views.updateOne(
  { orderId: "CMD-1" },
  { $setOnInsert: {
      orderId: "CMD-1", customerId: "CLI-1", createdAt: new Date("2026-10-29T10:00:00Z"),
      total: 129.0, itemsCount: 3,
      items: [ { productName: "Baskets urbaines", quantity: 2 }, { productName: "Casque audio", quantity: 1 } ],
      deliveryAddress: "8 rue Lafayette, 75009 Paris",
      status: "PENDING", statusLabel: "En attente", carrier: null, trackingNumber: null,
      timeline: [ { status: "PENDING", label: "Commande reçue", at: new Date("2026-10-29T10:00:00Z") } ],
      appliedEventIds: ["evt-0001"], updatedAt: new Date("2026-10-29T10:00:00Z") } },
  { upsert: true }));

log("evt-0002 OrderCreated CMD-2", db.order_views.updateOne(
  { orderId: "CMD-2" },
  { $setOnInsert: {
      orderId: "CMD-2", customerId: "CLI-2", createdAt: new Date("2026-10-29T10:10:00Z"),
      total: 250.0, itemsCount: 10,
      items: [ { productName: "Baskets urbaines", quantity: 10 } ],
      deliveryAddress: "5 avenue Foch, 75016 Paris",
      status: "PENDING", statusLabel: "En attente", carrier: null, trackingNumber: null,
      timeline: [ { status: "PENDING", label: "Commande reçue", at: new Date("2026-10-29T10:10:00Z") } ],
      appliedEventIds: ["evt-0002"], updatedAt: new Date("2026-10-29T10:10:00Z") } },
  { upsert: true }));

log("evt-0003 OrderCreated CMD-3", db.order_views.updateOne(
  { orderId: "CMD-3" },
  { $setOnInsert: {
      orderId: "CMD-3", customerId: "CLI-1", createdAt: new Date("2026-10-29T11:00:00Z"),
      total: 45.0, itemsCount: 3,
      items: [ { productName: "Gourde isotherme", quantity: 3 } ],
      deliveryAddress: "8 rue Lafayette, 75009 Paris",
      status: "PENDING", statusLabel: "En attente", carrier: null, trackingNumber: null,
      timeline: [ { status: "PENDING", label: "Commande reçue", at: new Date("2026-10-29T11:00:00Z") } ],
      appliedEventIds: ["evt-0003"], updatedAt: new Date("2026-10-29T11:00:00Z") } },
  { upsert: true }));

log("evt-0004 OrderCreated CMD-4", db.order_views.updateOne(
  { orderId: "CMD-4" },
  { $setOnInsert: {
      orderId: "CMD-4", customerId: "CLI-3", createdAt: new Date("2026-10-29T11:30:00Z"),
      total: 149.0, itemsCount: 1,
      items: [ { productName: "Montre connectee", quantity: 1 } ],
      deliveryAddress: "12 rue de Rivoli, 75004 Paris",
      status: "PENDING", statusLabel: "En attente", carrier: null, trackingNumber: null,
      timeline: [ { status: "PENDING", label: "Commande reçue", at: new Date("2026-10-29T11:30:00Z") } ],
      appliedEventIds: ["evt-0004"], updatedAt: new Date("2026-10-29T11:30:00Z") } },
  { upsert: true }));

// ---------------------------------------------------------------------
// 2. Réponses du Stock : nouveau statut + ajout d'une étape à la timeline
// ---------------------------------------------------------------------
log("evt-0005 StockReserved CMD-1", db.order_views.updateOne(
  { orderId: "CMD-1", appliedEventIds: { $ne: "evt-0005" } },
  { $set: { status: "CONFIRMED", statusLabel: "Confirmée", updatedAt: new Date("2026-10-29T10:00:02Z") },
    $push: { timeline: { status: "CONFIRMED", label: "Stock réservé", at: new Date("2026-10-29T10:00:02Z") },
             appliedEventIds: "evt-0005" } }));

log("evt-0006 StockRejected CMD-2", db.order_views.updateOne(
  { orderId: "CMD-2", appliedEventIds: { $ne: "evt-0006" } },
  { $set: { status: "REJECTED", statusLabel: "Refusée", updatedAt: new Date("2026-10-29T10:10:02Z") },
    $push: { timeline: { status: "REJECTED", label: "Stock insuffisant", at: new Date("2026-10-29T10:10:02Z") },
             appliedEventIds: "evt-0006" } }));

log("evt-0007 StockReserved CMD-3", db.order_views.updateOne(
  { orderId: "CMD-3", appliedEventIds: { $ne: "evt-0007" } },
  { $set: { status: "CONFIRMED", statusLabel: "Confirmée", updatedAt: new Date("2026-10-29T11:00:02Z") },
    $push: { timeline: { status: "CONFIRMED", label: "Stock réservé", at: new Date("2026-10-29T11:00:02Z") },
             appliedEventIds: "evt-0007" } }));

log("evt-0008 StockReserved CMD-4", db.order_views.updateOne(
  { orderId: "CMD-4", appliedEventIds: { $ne: "evt-0008" } },
  { $set: { status: "CONFIRMED", statusLabel: "Confirmée", updatedAt: new Date("2026-10-29T11:30:02Z") },
    $push: { timeline: { status: "CONFIRMED", label: "Stock réservé", at: new Date("2026-10-29T11:30:02Z") },
             appliedEventIds: "evt-0008" } }));

// ---------------------------------------------------------------------
// 3. DeliveryAddressChanged_v1 : nouvelle adresse (pas de changement de statut)
// ---------------------------------------------------------------------
log("evt-0009 DeliveryAddressChanged CMD-3", db.order_views.updateOne(
  { orderId: "CMD-3", appliedEventIds: { $ne: "evt-0009" } },
  { $set: { deliveryAddress: "20 rue Oberkampf, 75011 Paris", updatedAt: new Date("2026-10-29T12:00:00Z") },
    $push: { appliedEventIds: "evt-0009" } }));

// ---------------------------------------------------------------------
// 4. OrderShipped_v1 : données venant du service Livraison
// ---------------------------------------------------------------------
log("evt-0010 OrderShipped CMD-1", db.order_views.updateOne(
  { orderId: "CMD-1", appliedEventIds: { $ne: "evt-0010" } },
  { $set: { status: "SHIPPED", statusLabel: "Expédiée", carrier: "Colissimo", trackingNumber: "6A12345678901",
            updatedAt: new Date("2026-10-29T14:00:00Z") },
    $push: { timeline: { status: "SHIPPED", label: "Expédiée", at: new Date("2026-10-29T14:00:00Z") },
             appliedEventIds: "evt-0010" } }));

// Contrôle
db.order_views.find({}, { _id: 0, orderId: 1, customerId: 1, statusLabel: 1 }).sort({ orderId: 1 })
  .forEach(v => print(v.orderId + " | " + v.customerId + " | " + v.statusLabel));
