package com.hamondev.shevery.api;

import android.os.SystemClock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock-free cache of {@link ClientPermissions} flags keyed by {@code (uid, packageName)}.
 *
 * <p>The server is notified about uid state changes (active / idle / cached) many times per
 * second; previously every notification cost one {@code getPackageInfo(GET_PERMISSIONS)}
 * binder call per package of the uid. With this cache a hot uid costs a map lookup.
 *
 * <p>Invalidation: callers must call {@link #invalidateUid(int)} when a uid goes away
 * (package updates and uninstalls always kill the uid first). A TTL bounds staleness
 * for anything that slips through.
 */
public final class ClientPermissionCache {

    /** Source of truth, typically {@code PackageManager.getPackageInfo(GET_PERMISSIONS)}. */
    public interface Source {
        /**
         * @return requested permissions of the package, or {@code null} if unknown.
         * @throws Exception on transient failures; the result is not cached then.
         */
        String[] getRequestedPermissions(String packageName, int userId) throws Exception;
    }

    public static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    private static final class Entry {
        final int flags;
        final long expiresAt;

        Entry(int flags, long expiresAt) {
            this.flags = flags;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, Entry>> entries = new ConcurrentHashMap<>();
    private final Source source;
    private final long ttlMs;

    public ClientPermissionCache(Source source) {
        this(source, DEFAULT_TTL_MS);
    }

    public ClientPermissionCache(Source source, long ttlMs) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        this.source = source;
        this.ttlMs = ttlMs;
    }

    /** Returns {@link ClientPermissions} flags of {@code packageName} running as {@code uid}. */
    public int get(int uid, String packageName) {
        long now = SystemClock.uptimeMillis();
        Map<String, Entry> byPackage = entries.get(uid);
        Entry entry = byPackage != null ? byPackage.get(packageName) : null;
        if (entry != null && entry.expiresAt > now) {
            return entry.flags;
        }

        String[] requested;
        try {
            requested = source.getRequestedPermissions(packageName, SheveryApi.getUserId(uid));
        } catch (Throwable tr) {
            return entry != null ? entry.flags : ClientPermissions.NONE;
        }
        int flags = ClientPermissions.classify(requested);
        store(uid, packageName, flags, now);
        return flags;
    }

    /** Seeds the cache from an already fetched permission list (e.g. bulk enumeration). */
    public int put(int uid, String packageName, String[] requestedPermissions) {
        int flags = ClientPermissions.classify(requestedPermissions);
        store(uid, packageName, flags, SystemClock.uptimeMillis());
        return flags;
    }

    public void invalidateUid(int uid) {
        entries.remove(uid);
    }

    public void invalidate(int uid, String packageName) {
        Map<String, Entry> byPackage = entries.get(uid);
        if (byPackage != null) {
            byPackage.remove(packageName);
        }
    }

    public void clear() {
        entries.clear();
    }

    private void store(int uid, String packageName, int flags, long now) {
        ConcurrentHashMap<String, Entry> byPackage = entries.get(uid);
        if (byPackage == null) {
            ConcurrentHashMap<String, Entry> created = new ConcurrentHashMap<>(2);
            byPackage = entries.putIfAbsent(uid, created);
            if (byPackage == null) {
                byPackage = created;
            }
        }
        byPackage.put(packageName, new Entry(flags, now + ttlMs));
    }
}
