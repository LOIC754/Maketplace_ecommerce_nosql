// Sélection de la base du service Commande
db = db.getSiblingDB("order_db");

// Script idempotent : on repart d'une collection vide
db.orders.drop();

// Agrégats Commande, tous au statut PENDING : les événements les feront évoluer
db.orders.insertMany([
  {
    orderId: "CMD-1",
    customerId: "CLI-1",
    lines: [
      { productId: "PROD-1", productName: "Baskets urbaines", quantity: 2, unitPrice: 25.0 },
      { productId: "PROD-2", productName: "Casque audio", quantity: 1, unitPrice: 79.0 }
    ],
    total: 129.0,
    deliveryAddress: "8 rue Lafayette, 75009 Paris",
    status: "PENDING",
    createdAt: new Date("2026-10-29T10:00:00Z"),
    updatedAt: new Date("2026-10-29T10:00:00Z")
  },
  {
    // Cas de survente : 10 articles demandés, seulement 3 en stock
    orderId: "CMD-2",
    customerId: "CLI-2",
    lines: [
      { productId: "PROD-1", productName: "Baskets urbaines", quantity: 10, unitPrice: 25.0 }
    ],
    total: 250.0,
    deliveryAddress: "5 avenue Foch, 75016 Paris",
    status: "PENDING",
    createdAt: new Date("2026-10-29T10:10:00Z"),
    updatedAt: new Date("2026-10-29T10:10:00Z")
  },
  {
    // Deuxième commande du client CLI-1 : sert au tri par date de R4
    orderId: "CMD-3",
    customerId: "CLI-1",
    lines: [
      { productId: "PROD-5", productName: "Gourde isotherme", quantity: 3, unitPrice: 15.0 }
    ],
    total: 45.0,
    deliveryAddress: "8 rue Lafayette, 75009 Paris",
    status: "PENDING",
    createdAt: new Date("2026-10-29T11:00:00Z"),
    updatedAt: new Date("2026-10-29T11:00:00Z")
  },
  {
    orderId: "CMD-4",
    customerId: "CLI-3",
    lines: [
      { productId: "PROD-4", productName: "Montre connectee", quantity: 1, unitPrice: 149.0 }
    ],
    total: 149.0,
    deliveryAddress: "12 rue de Rivoli, 75004 Paris",
    status: "PENDING",
    createdAt: new Date("2026-10-29T11:30:00Z"),
    updatedAt: new Date("2026-10-29T11:30:00Z")
  }
]);

// Contrôle du chargement
print("Commandes chargées : " + db.orders.countDocuments());