package com.marketplace.stock.domain;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// Accès à l'agrégat Stock : un Hash Redis par produit (stock:<productId>)
// et le Set stock:products qui liste le catalogue.
@Repository
public class StockRepository {

    static final String CATALOG = "stock:products";

    private final StringRedisTemplate redis;

    public StockRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String key(String productId) {
        return "stock:" + productId;
    }

    // R2 : HGETALL stock:<productId> (Map vide si le produit n'existe pas)
    public Map<Object, Object> find(String productId) {
        return redis.opsForHash().entries(key(productId));
    }

    // Catalogue : SMEMBERS stock:products, puis HGETALL de chaque produit
    public List<Map<Object, Object>> findAll() {
        List<Map<Object, Object>> products = new ArrayList<>();
        var ids = redis.opsForSet().members(CATALOG);
        if (ids == null) return products;
        for (String id : new TreeSet<>(ids)) {          // tri par identifiant
            products.add(find(id));
        }
        return products;
    }

    // W4 : réapprovisionnement, HINCRBY atomique sur available
    // Retourne la nouvelle quantité disponible, ou null si le produit est inconnu
    public Long restock(String productId, int quantity) {
        if (!Boolean.TRUE.equals(redis.hasKey(key(productId)))) return null;
        Long available = redis.opsForHash().increment(key(productId), "available", quantity);
        redis.opsForHash().put(key(productId), "updatedAt", Instant.now().toString());
        return available;
    }
}
