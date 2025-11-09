package com.example.weathersdk.cache;

import java.util.Optional;

/**
 * Generic cache interface used by the SDK.  Provides methods to retrieve,
 * store and iterate over cached values.  Implementations can vary from
 * simple in‑memory maps to distributed caches.
 */
public interface Cache<K, V> {
    /** Returns a cached value for the given key, if present. */
    Optional<V> get(K key);
    /** Stores a value in the cache, potentially evicting older entries. */
    void put(K key, V value);
    /** Removes all entries from the cache. */
    void clear();
    /** Returns an iterable over the keys currently stored in the cache. */
    Iterable<K> keys();
}