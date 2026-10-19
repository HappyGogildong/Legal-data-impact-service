package com.lia.core.testsupport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lia.core.domain.law.Law;

/** 저장 상태를 만들기 위한 최소 법령. revision 은 {@code "rev-" + mst}. */
public final class TestLaws {

    private TestLaws() {}

    public static Law law(String lawId, String mst, Law.Status status, LocalDate effectiveDate) {
        return new Law(lawId, mst, "테스트법", status, Law.AmendKind.일부개정, Law.LawType.법률, "부처",
                LocalDate.of(2026, 1, 1), "1", effectiveDate, "공포 후 시행", Law.EnforcementType.유예,
                "개정이유", "개정문", List.of(), List.of(), List.of(), null, "url", "rev-" + mst, Instant.now());
    }
}
