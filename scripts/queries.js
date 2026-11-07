// =====================================================================
// Requêtes d'interrogation MongoDB (motifs d'accès R1, R3, R4 + analyses)
// Chaque requête affiche son résultat puis son plan d'exécution.
// =====================================================================

// Résume le plan d'exécution : enchaînement des étapes + documents lus / renvoyés
function plan(cursor) {
  const e = cursor.explain("executionStats");
  let s = e.queryPlanner.winningPlan, steps = [];
  while (s) { steps.push(s.stage + (s.indexName ? "(" + s.indexName + ")" : "")); s = s.inputStage; }
  print("   plan : " + steps.join(" <- ") +
        " | lus : " + e.executionStats.totalDocsExamined + ", renvoyés : " + e.executionStats.nReturned);
}
const fmt = d => d.toISOString().slice(0, 16).replace("T", " ");

// ---------------------------------------------------------------------
// R1 : obtenir une commande par son identifiant (service Commande)
// ---------------------------------------------------------------------
const orders = db.getSiblingDB("order_db").orders;
print("\n=== R1 : commande CMD-1 (order_db.orders)");
const o = orders.findOne({ orderId: "CMD-1" }, { _id: 0, appliedEventIds: 0 });
print("   " + o.orderId + " | client " + o.customerId + " | " + o.status + " | total " + o.total + " EUR | " + o.deliveryAddress);
o.lines.forEach(l => print("     - " + l.productName + " x" + l.quantity + " a " + l.unitPrice + " EUR"));
plan(orders.find({ orderId: "CMD-1" }));

// ---------------------------------------------------------------------
// R3 : commandes à expédier, les plus anciennes d'abord (service Livraison)
// Jointure LOCALE : livraisons + réplica, tous deux dans delivery_db.
// Livraison obtient l'adresse et les articles sans interroger Commande.
// ---------------------------------------------------------------------
const delivery = db.getSiblingDB("delivery_db");
print("\n=== R3 : livraisons à préparer (delivery_db.deliveries + order_replicas)");
delivery.deliveries.aggregate([
  { $match: { status: "WAITING" } },
  { $sort: { createdAt: 1 } },
  { $lookup: { from: "order_replicas", localField: "orderId", foreignField: "orderId", as: "order" } },
  { $unwind: "$order" },
  { $project: { _id: 0, deliveryId: 1, orderId: 1, createdAt: 1, address: "$order.deliveryAddress", items: "$order.items" } }
]).forEach(d => print("   " + d.deliveryId + " | " + d.orderId + " | depuis " + fmt(d.createdAt) + " | " + d.address +
                      " | " + d.items.map(i => i.productName + " x" + i.quantity).join(", ")));
plan(delivery.deliveries.find({ status: "WAITING" }).sort({ createdAt: 1 }));

// ---------------------------------------------------------------------
// R4 : « Mes commandes » d'un client, les plus récentes d'abord (service Query)
// ---------------------------------------------------------------------
const views = db.getSiblingDB("query_db").order_views;
print("\n=== R4 : Mes commandes du client CLI-1 (query_db.order_views)");
views.find({ customerId: "CLI-1" }, { _id: 0, orderId: 1, createdAt: 1, total: 1, itemsCount: 1, statusLabel: 1 })
  .sort({ createdAt: -1 }).limit(20)
  .forEach(v => print("   " + v.orderId + " | " + fmt(v.createdAt) + " | " + v.itemsCount + " article(s) | " + v.total + " EUR | " + v.statusLabel));
plan(views.find({ customerId: "CLI-1" }).sort({ createdAt: -1 }).limit(20));

print("\n=== R4 (détail) : suivi de la commande CMD-1");
views.findOne({ orderId: "CMD-1" }).timeline
  .forEach(t => print("   " + fmt(t.at) + " | " + t.label));

// ---------------------------------------------------------------------
// Analyses complémentaires
// ---------------------------------------------------------------------
print("\n=== Commandes contenant le produit PROD-1 (notation pointée dans un tableau)");
orders.find({ "lines.productId": "PROD-1" }, { _id: 0, orderId: 1, status: 1 })
  .forEach(x => print("   " + x.orderId + " | " + x.status));

print("\n=== Chiffre d'affaires par statut (agrégation $group)");
orders.aggregate([
  { $group: { _id: "$status", commandes: { $sum: 1 }, montant: { $sum: "$total" } } },
  { $sort: { _id: 1 } }
]).forEach(g => print("   " + g._id + " | " + g.commandes + " commande(s) | " + g.montant + " EUR"));
