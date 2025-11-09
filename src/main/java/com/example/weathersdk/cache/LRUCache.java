package com.example.weathersdk.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simple thread‑safe LRU cache backed by {@link LinkedHashMap}.  The cache
 * evicts the least‑recently used entry when the maximum size is exceeded.
 */
public class LRUCache<K, V> implements Cache<K, V> {
    private final Map<K, V> delegate;
    private final int maxSize;

    public LRUCache(int maxSize) {
        this.maxSize = maxSize;
        this.delegate = Collections.synchronizedMap(new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LRUCache.this.maxSize;
            }
        });
    }

    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(delegate.get(key));
    }

    @Override
    public void put(K key, V value) {
        delegate.put(key, value);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Iterable<K> keys() {
        synchronized (delegate) {
            return Collections.unmodifiableList(new ArrayList<>(delegate.keySet()));
        }
    }
}