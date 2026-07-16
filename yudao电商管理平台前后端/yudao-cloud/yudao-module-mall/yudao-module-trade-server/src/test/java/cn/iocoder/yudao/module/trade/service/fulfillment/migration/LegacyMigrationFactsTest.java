package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyMigrationFactsTest {

    @Test
    void toStringDoesNotExposeApprovalSourceReference() {
        LegacyMigrationFacts facts = new LegacyMigrationFacts("US", "US", "America/New_York",
                "America/Los_Angeles", 501L, 601L, 701L,
                LocalDateTime.of(2026, 7, 16, 10, 0), "secret-approval-reference");

        assertFalse(facts.toString().contains("secret-approval-reference"));
    }
}
