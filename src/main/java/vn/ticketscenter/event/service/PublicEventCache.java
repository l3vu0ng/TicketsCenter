package vn.ticketscenter.event.service;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Cache-aside for anonymous event reads; DB remains the source of truth. */
public final class PublicEventCache {
    private static final Logger LOGGER = Logger.getLogger(PublicEventCache.class.getName());
    private static final long TTL_SECONDS = 60;
    private static final String PREFIX = "public-event:";
    private final Store store;

    public PublicEventCache() {
        this(new RedisStore());
    }

    PublicEventCache(Store store) {
        this.store = store;
    }

    public String eventKey(UUID eventId) {
        return PREFIX + eventId + ":detail";
    }

    public String zonesKey(UUID eventId) {
        return PREFIX + eventId + ":zones";
    }

    public String get(String key) {
        try {
            return store.get(key);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Public event cache read failed; using database", exception);
            return null;
        }
    }

    public void put(String key, String value) {
        try {
            store.put(key, value, TTL_SECONDS);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Public event cache write failed", exception);
        }
    }

    public void invalidate(UUID eventId) {
        try {
            store.delete(eventKey(eventId), zonesKey(eventId));
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Public event cache invalidation failed; TTL bounds staleness", exception);
        }
    }

    interface Store {
        String get(String key);
        void put(String key, String value, long ttlSeconds);
        void delete(String... keys);
    }

    private static final class RedisStore implements Store {
        @Override public String get(String key) {
            try (var jedis = vn.ticketscenter.config.redis.RedisClientProvider.getInstance().getResource()) {
                return jedis.get(key);
            }
        }

        @Override public void put(String key, String value, long ttlSeconds) {
            try (var jedis = vn.ticketscenter.config.redis.RedisClientProvider.getInstance().getResource()) {
                jedis.setex(key, ttlSeconds, value);
            }
        }

        @Override public void delete(String... keys) {
            try (var jedis = vn.ticketscenter.config.redis.RedisClientProvider.getInstance().getResource()) {
                jedis.del(keys);
            }
        }
    }
}
