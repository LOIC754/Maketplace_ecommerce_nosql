#!/usr/bin/env bash
# =====================================================================
# Démonstration : les deux bases sont remplies et interrogeables.
# Exécute les motifs d'accès R1 à R4 (et quelques analyses).
# Prérequis : bash scripts/load-all.sh
# Usage (à la racine du dépôt) : bash scripts/run-queries.sh
# =====================================================================

# Raccourcis vers les deux bases (le tr supprime d'éventuels \r Windows)
r() { docker compose exec -T redis redis-cli "$@" | tr -d '\r'; }
m() { docker compose exec -T mongodb mongosh --quiet --eval "$1" | tr -d '\r'; }

# ---------------------------------------------------------------------
# R2 : stock disponible d'un produit (service Stock, Redis)
# ---------------------------------------------------------------------
echo "=== R2 : stock disponible de PROD-1 (Redis, HGET stock:PROD-1 available)"
echo "   PROD-1 : $(r HGET stock:PROD-1 available) disponible(s)"

echo
echo "=== Catalogue complet avec disponibilités (Redis : SMEMBERS puis HMGET)"
for p in $(r SMEMBERS stock:products | sort); do
  # HMGET renvoie les champs demandés, un par ligne
  mapfile -t f < <(r HMGET "stock:$p" name price available reserved)
  [ "${f[2]}" = "0" ] && etat="RUPTURE" || etat="en stock"
  echo "   $p | ${f[0]} | ${f[1]} EUR | disponible : ${f[2]} | réservé : ${f[3]} | $etat"
done

echo
echo "=== Événements du journal par type (Redis : LRANGE shop:events:archive)"
r LRANGE shop:events:archive 0 -1 | grep -o '"eventType":"[A-Za-z]*"' | cut -d'"' -f4 | sort | uniq -c | sed 's/^/   /'

# ---------------------------------------------------------------------
# R1, R3, R4 et analyses (MongoDB)
# ---------------------------------------------------------------------
m "$(cat scripts/queries.js)"
