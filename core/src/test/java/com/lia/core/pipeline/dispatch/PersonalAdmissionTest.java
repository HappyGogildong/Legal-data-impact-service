package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import com.lia.core.domain.law.Law;
import org.junit.jupiter.api.Test;

class PersonalAdmissionTest {

    static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static final class CountingQuota implements PersonalizationQuota {
        boolean allow = true;
        int consumed;
        public boolean tryConsume(UUID userId) {
            if (!allow) return false;
            consumed++;
            return true;
        }
    }

    @Test
    void 개정본인데_기준선이_없으면_한도를_건드리지_않고_BASELINE_NOT_LOADED() {
        CountingQuota quota = new CountingQuota();
        Optional<Unmet> r = PersonalAdmission.admit(USER, QueryDispatcherTest.law(Law.AmendKind.일부개정), null, quota);
        assertEquals(Unmet.Reason.BASELINE_NOT_LOADED, r.orElseThrow().reason());
        assertEquals(0, quota.consumed);
    }

    @Test
    void 제정은_기준선_없이_통과하고_한도_1회_차감() {
        CountingQuota quota = new CountingQuota();
        assertTrue(PersonalAdmission.admit(USER, QueryDispatcherTest.law(Law.AmendKind.제정), null, quota).isEmpty());
        assertEquals(1, quota.consumed);
    }

    @Test
    void 한도가_없으면_DAILY_LIMIT() {
        CountingQuota quota = new CountingQuota();
        quota.allow = false;
        Law law = QueryDispatcherTest.law(Law.AmendKind.일부개정);
        assertEquals(Unmet.Reason.DAILY_LIMIT, PersonalAdmission.admit(USER, law, law, quota).orElseThrow().reason());
    }
}
