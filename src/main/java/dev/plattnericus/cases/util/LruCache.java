package dev.plattnericus.cases.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Size-bounded, thread-safe LRU cache. */
public final class LruCache<K, V> {

    private final int maxEntries;
    private final LinkedHashMap<K, V> map;

    public LruCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.maxEntries;
            }
        };
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized void put(K key, V value) {
        map.put(key, value);
    }

    public synchronized void clear() {
        map.clear();
    }

    public synchronized int size() {
        return map.size();
    }
}
