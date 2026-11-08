"""
Routeur d'événements (fan-out) de la marketplace.

Il lit la file d'entrée unique shop:events:published et dépose une copie de
chaque événement dans la file `ready` de chaque service abonné, plus dans
l'archive. Même protocole que le routeur du projet de l'enseignant :

    LMOVE published -> router:processing   (prise en charge atomique)
    RPUSH une copie par abonnement          (fan-out)
    LREM router:processing                  (acquittement)

Une panne pendant le fan-out peut livrer un événement deux fois à certains
services : chaque service consommateur reste donc idempotent (eventId).
"""

import json
import logging
import os
import signal

import redis

# --- Clés Redis ------------------------------------------------------------
PUBLISHED = "shop:events:published"      # file d'entrée de tous les événements
PROCESSING = "shop:router:processing"    # événement en cours de routage
ARCHIVE = "shop:events:archive"          # journal non consommé (relecture)
DEAD_LETTER = "shop:events:deadletter"   # événements illisibles ou sans abonné

# --- Table de routage : type d'événement -> files des services abonnés ------
# Reprend exactement la liste des événements de l'étape 4 (+ projection Query).
SUBSCRIPTIONS = {
    "OrderCreated":           ["shop:stock:ready", "shop:delivery:ready", "shop:query:ready"],
    "StockReserved":          ["shop:order:ready", "shop:delivery:ready", "shop:query:ready"],
    "StockRejected":          ["shop:order:ready", "shop:delivery:ready", "shop:query:ready"],
    "DeliveryAddressChanged": ["shop:delivery:ready", "shop:query:ready"],
    "OrderShipped":           ["shop:order:ready", "shop:query:ready"],
}

# Attente maximale de BLMOVE avant de reboucler (secondes)
BLOCK_TIMEOUT = 5

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("event-router")

running = True


def stop(signum, frame):
    """Arrêt propre sur SIGTERM (docker compose stop) ou Ctrl+C."""
    global running
    log.info("Arrêt demandé, fin après l'événement en cours")
    running = False


def recover(r: redis.Redis) -> None:
    """Reprise après panne : remet en tête de published les événements restés
    dans router:processing (routeur arrêté avant l'acquittement)."""
    count = 0
    while r.lmove(PROCESSING, PUBLISHED, "RIGHT", "LEFT") is not None:
        count += 1
    if count:
        log.info("Reprise : %d événement(s) remis dans %s", count, PUBLISHED)


def targets_for(raw: str) -> tuple[str, list[str]]:
    """Retourne (description, files cibles) pour un événement brut.
    Un événement illisible ou sans abonné part en dead-letter."""
    try:
        event = json.loads(raw)
        event_id, event_type = event["eventId"], event["eventType"]
    except (ValueError, KeyError, TypeError):
        return "événement illisible", [DEAD_LETTER]
    queues = SUBSCRIPTIONS.get(event_type)
    if not queues:
        return f"{event_id} {event_type} sans abonné", [DEAD_LETTER]
    return f"{event_id} {event_type}", queues + [ARCHIVE]


def route_one(r: redis.Redis) -> bool:
    """Route un événement. Retourne False si la file est restée vide."""
    # BLMOVE : prise en charge atomique, en attendant qu'un événement arrive
    # (évite d'interroger Redis en boucle quand il n'y a rien à faire)
    raw = r.blmove(PUBLISHED, PROCESSING, BLOCK_TIMEOUT, "LEFT", "RIGHT")
    if raw is None:
        return False

    label, queues = targets_for(raw)

    # Fan-out : une copie par file cible, envoyées en un seul aller-retour
    pipe = r.pipeline(transaction=False)
    for queue in queues:
        pipe.rpush(queue, raw)
    pipe.execute()

    # Acquittement seulement après les copies
    r.lrem(PROCESSING, 1, raw)
    log.info("%s -> %s", label, ", ".join(queues))
    return True


def main() -> None:
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    r = redis.Redis(
        host=os.getenv("REDIS_HOST", "localhost"),
        port=int(os.getenv("REDIS_PORT", "6380")),
        decode_responses=True,
    )
    r.ping()
    log.info("Routeur connecté à Redis, écoute de %s", PUBLISHED)

    recover(r)
    while running:
        route_one(r)


if __name__ == "__main__":
    main()
