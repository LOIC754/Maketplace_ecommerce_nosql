#!/usr/bin/env bash
# =====================================================================
# Rejoue tout le scénario du niveau 1, dans l'ordre, depuis zéro.
# Usage (à la racine du dépôt) : bash scripts/load-all.sh
# =====================================================================
set -e

# Exécute un script Redis
redis() { echo ">>> Redis : $1"; docker compose exec -T redis redis-cli < "data/redis/$1"; }
# Exécute un script MongoDB
mongo() { echo ">>> MongoDB : $1"; docker compose exec -T mongodb mongosh --quiet --eval "$(cat "data/mongo/$1")"; }

# Données initiales
redis 01-stock.redis
mongo 01-orders.js
mongo 02-indexes.js

# Commande publie OrderCreated, le routeur distribue
redis 02-events-order-created.redis

# Stock réserve et répond
redis 03-stock-reservation.redis

# Livraison crée et met à jour le réplica, crée les livraisons
redis 04-delivery-take.redis
mongo 03-delivery-replicas.js
redis 05-delivery-ack.redis

# Commande applique les réponses du Stock et change une adresse
redis 06-order-take.redis
mongo 04-order-updates.js
redis 07-order-ack-address-event.redis

# Livraison met à jour le réplica (adresse) et expédie CMD-1
mongo 05-delivery-address-and-ship.js
redis 08-delivery-ack-shipped-event.redis

# Commande applique l'expédition
mongo 06-order-shipped.js
redis 09-order-ack-shipped.redis

echo ">>> Scénario chargé"
