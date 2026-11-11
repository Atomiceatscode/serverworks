package dev.serverworks.catalog.http;

import java.util.LinkedHashMap;
import java.util.Map;

/** A small least-recently-used cache whose entries also expire after a fixed time. */
public final class TtlCache<V> {
	private final long ttlMillis;
	private final LinkedHashMap<String, Slot<V>> entries;

	public TtlCache(int maxEntries, long ttlMillis) {
		this.ttlMillis = ttlMillis;
		this.entries = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Slot<V>> eldest) {
				return size() > maxEntries;
			}
		};
	}

	/** The cached value, or null when absent or expired. */
	public synchronized V get(String key, long nowMillis) {
		Slot<V> entry = entries.get(key);
		if (entry == null) {
			return null;
		}
		if (nowMillis - entry.storedAtMillis >= ttlMillis) {
			entries.remove(key);
			return null;
		}
		return entry.value;
	}

	public synchronized void put(String key, V value, long nowMillis) {
		entries.put(key, new Slot<>(value, nowMillis));
	}

	public synchronized int size() {
		return entries.size();
	}

	private record Slot<V>(V value, long storedAtMillis) {
	}
}
