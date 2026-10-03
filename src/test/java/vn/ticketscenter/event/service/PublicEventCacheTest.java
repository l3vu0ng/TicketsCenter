package vn.ticketscenter.event.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicEventCacheTest {
    @Test
    void invalidation_deletes_both_public_representations_instead_of_rewriting_them() {
        List<String> deleted = new ArrayList<>();
        PublicEventCache cache = new PublicEventCache(new PublicEventCache.Store() {
            @Override public String get(String key) { return null; }
            @Override public void put(String key, String value, long ttlSeconds) { }
            @Override public void delete(String... keys) { deleted.addAll(List.of(keys)); }
        });
        UUID eventId = UUID.randomUUID();

        cache.invalidate(eventId);

        assertEquals(List.of(cache.eventKey(eventId), cache.zonesKey(eventId)), deleted);
    }
}
