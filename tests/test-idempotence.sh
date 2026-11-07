#!/usr/bin/env bash
# =====================================================================
# Démonstration de l'idempotence : on livre une 2e fois des événements
# déjà traités et on vérifie que rien ne change.
# Prérequis : bash scripts/load-all.sh
# Usage (à la racine du dépôt) : bash tests/test-idempotence.sh
# =====================================================================

FAILS=0

# Raccourcis vers les deux bases (le tr supprime d'éventuels \r Windows)
r() { docker compose exec -T redis redis-cli "$@" | tr -d '\r'; }
m() { docker compose exec -T mongodb mongosh --quiet --eval "$1" | tr -d '\r'; }

# Compare une valeur attendue et une valeur obtenue
check() {
  if [ "$2" = "$3" ]; then echo "OK    $1"; else echo "ECHEC $1 (attendu: $2, obtenu: $3)"; FAILS=$((FAILS+1)); fi
}

echo "=== 1. Stock (Redis) : réservation de evt-0001 rejouée"
LUA="local n=#KEYS-1 if redis.call('SADD',KEYS[1],ARGV[1])==0 then return -1 end for i=1,n do local a=tonumber(redis.call('HGET',KEYS[i+1],'available')) if (not a) or a<tonumber(ARGV[i+2]) then return 0 end end for i=1,n do redis.call('HINCRBY',KEYS[i+1],'available',-tonumber(ARGV[i+2])) redis.call('HINCRBY',KEYS[i+1],'reserved',tonumber(ARGV[i+2])) redis.call('HSET',KEYS[i+1],'updatedAt',ARGV[2]) end return 1"
BEFORE=$(r HMGET stock:PROD-1 available reserved | tr '\n' ' ')
RES=$(r EVAL "$LUA" 3 shop:stock:processed stock:PROD-1 stock:PROD-2 evt-0001 2026-10-29T10:00:02Z 2 1)
AFTER=$(r HMGET stock:PROD-1 available reserved | tr '\n' ' ')
check "Stock     | le script Lua renvoie -1 (déjà traité)" "-1" "$RES"
check "Stock     | PROD-1 inchangé (available reserved)" "$BEFORE" "$AFTER"

echo "=== 2. Livraison : evt-0009 relu depuis l'archive et relivré par la file"
# Replay : on relit l'événement dans le journal, on ne le réécrit pas à la main
EV=$(r --raw LINDEX shop:events:archive 8)
check "Archive   | l'événement relu est bien evt-0009" "1" "$(echo "$EV" | grep -c '"eventId":"evt-0009"')"
r RPUSH shop:delivery:ready "$EV" > /dev/null
r LMOVE shop:delivery:ready shop:delivery:processing LEFT RIGHT > /dev/null
MOD=$(m 'print(db.getSiblingDB("delivery_db").order_replicas.updateOne({ orderId: "CMD-3", appliedEventIds: { $ne: "evt-0009" } }, { $set: { deliveryAddress: "20 rue Oberkampf, 75011 Paris" }, $push: { appliedEventIds: "evt-0009" } }).modifiedCount)')
check "Livraison | evt-0009 relivré : réplica non modifié" "0" "$MOD"
r LREM shop:delivery:processing 1 "$EV" > /dev/null
check "Livraison | file processing vide après acquittement" "0" "$(r LLEN shop:delivery:processing)"

echo "=== 3. MongoDB : rejeu des événements dans chaque service"
OUT=$(m "$(cat tests/idempotence.js)")
echo "$OUT"
FAILS=$((FAILS + $(echo "$OUT" | grep -c '^ECHEC')))
# Sécurité : si MongoDB n'a pas répondu, aucun test n'a pu s'exécuter
check "MongoDB   | les 10 tests ont bien été exécutés" "10" "$(echo "$OUT" | grep -cE '^(OK|ECHEC)')"

echo "==="
if [ "$FAILS" -eq 0 ]; then echo "Tous les tests d'idempotence sont passés"; else echo "$FAILS test(s) en échec"; fi
