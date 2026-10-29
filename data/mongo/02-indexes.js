// Index du service Commande : R1, une commande par identifiant
db = db.getSiblingDB("order_db");
db.orders.createIndex({ orderId: 1 }, { unique: true, name: "uk_orderId" });

// Index du service Livraison
db = db.getSiblingDB("delivery_db");
// Une seule livraison par commande
db.deliveries.createIndex({ orderId: 1 }, { unique: true, name: "uk_orderId" });
// R3 : livraisons à expédier, triées par date croissante
db.deliveries.createIndex({ status: 1, createdAt: 1 }, { name: "idx_status_createdAt" });
// Un seul réplica par commande
db.order_replicas.createIndex({ orderId: 1 }, { unique: true, name: "uk_orderId" });

// Index du service Query (projection)
db = db.getSiblingDB("query_db");
// Un seul document de vue par commande
db.order_views.createIndex({ orderId: 1 }, { unique: true, name: "uk_orderId" });
// R4 : commandes d'un client, les plus récentes d'abord
db.order_views.createIndex({ customerId: 1, createdAt: -1 }, { name: "idx_customerId_createdAt" });

print("Index créés");