package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorIdentityHasherTest {

    @Test
    void hash_isStableAndScopedByTenantAndVersion() throws Exception {
        TenantBehaviorHmacKeyProvider provider = (tenantId, version) -> {
            byte[] key = new byte[32];
            java.util.Arrays.fill(key, (byte) (tenantId.intValue() + version));
            return key;
        };
        BehaviorIdentityHasher hasher = new BehaviorIdentityHasher(provider);

        String first = hasher.hash(121L, 1, "visitor-1");

        assertEquals(64, first.length());
        assertEquals(first, hasher.hash(121L, 1, "visitor-1"));
        assertNotEquals(first, hasher.hash(122L, 1, "visitor-1"));
        assertNotEquals(first, hasher.hash(121L, 2, "visitor-1"));
        assertNotEquals(first, hasher.hash(121L, 1, "visitor-2"));
    }

    @Test
    void hash_rejectsBlankRawIdentity() {
        BehaviorIdentityHasher hasher = new BehaviorIdentityHasher((tenantId, version) -> new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(121L, 1, " "));
    }
}
