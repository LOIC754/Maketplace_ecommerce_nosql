-- =====================================================================
-- Réservation atomique du stock pour une commande (service Stock)
-- Tout s'exécute en UNE opération indivisible dans Redis :
--   1. déduplication de l'événement reçu (idempotence)
--   2. vérification de TOUS les produits (tout ou rien)
--   3. réservation de tous les produits
--   4. publication de l'événement de réponse dans shop:events:published
-- Comme le stock ET la file d'événements sont dans Redis, la réservation
-- et la publication de la réponse ne peuvent pas être séparées par une panne.
--
-- KEYS[1]    = shop:stock:processed  (Set des eventId déjà traités)
-- KEYS[2]    = shop:events:published (file d'entrée du routeur)
-- KEYS[3..n] = stock:<productId>     (un Hash par produit commandé)
-- ARGV[1]    = eventId reçu
-- ARGV[2]    = horodatage (updatedAt)
-- ARGV[3]    = événement StockReserved prêt à publier (JSON)
-- ARGV[4]    = modèle de l'événement StockRejected (JSON, complété ici)
-- ARGV[5..]  = quantités demandées, dans l'ordre des produits
--
-- Retour : 1 = réservé, 0 = refusé (stock insuffisant), -1 = déjà traité
-- =====================================================================
local nbProducts = #KEYS - 2

-- 1. Idempotence : un événement déjà traité ne fait rien
if redis.call('SADD', KEYS[1], ARGV[1]) == 0 then
  return -1
end

-- 2. Vérification de tous les produits avant toute modification
for i = 1, nbProducts do
  local key = KEYS[i + 2]
  local wanted = tonumber(ARGV[i + 4])
  local available = tonumber(redis.call('HGET', key, 'available')) or 0
  if available < wanted then
    -- Refus : on complète le modèle StockRejected et on le publie
    local rejected = cjson.decode(ARGV[4])
    rejected['payload']['productId'] = string.sub(key, 7)   -- retire le préfixe "stock:"
    rejected['payload']['requested'] = wanted
    rejected['payload']['available'] = available
    redis.call('RPUSH', KEYS[2], cjson.encode(rejected))
    return 0
  end
end

-- 3. Réservation de tous les produits
for i = 1, nbProducts do
  local key = KEYS[i + 2]
  local qty = tonumber(ARGV[i + 4])
  redis.call('HINCRBY', key, 'available', -qty)
  redis.call('HINCRBY', key, 'reserved', qty)
  redis.call('HSET', key, 'updatedAt', ARGV[2])
end

-- 4. Publication de StockReserved
redis.call('RPUSH', KEYS[2], ARGV[3])
return 1
