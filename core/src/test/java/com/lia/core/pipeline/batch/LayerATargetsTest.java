package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawDiffs;
import com.lia.core.store.LawSummaries;
import com.lia.core.testsupport.TestLaws;

class LayerATargetsTest {

    private static final LocalDate EF = LocalDate.of(2027, 1, 1);
    private static final Law AMENDMENT = TestLaws.law("A", "M1", Law.Status.시행예정, EF);
    private static final Law BASELINE = TestLaws.law("A", "B1", Law.Status.시행중, LocalDate.of(2026, 1, 1));
    private static final Law ENACTMENT = new Law("B", "M2", "새법", Law.Status.시행예정, Law.AmendKind.제정, Law.LawType.법률,
            "부처", LocalDate.of(2026, 1, 1), "1", EF, "공포 후 시행", Law.EnforcementType.유예, "이유", "개정문",
            List.of(), List.of(), List.of(), null, null, "rev-M2", Instant.now());
    private static final ImpactResult STORED = new ImpactResult("LAW:A", "SUMMARY", "저장본",
            List.of(), List.of(), List.of(), null, List.of(), "참고용", null);

    private static final LawSummaries NO_SUMMARY = law -> Optional.empty();
    private static final LawSummaries HAS_SUMMARY = law -> Optional.of(STORED);
    private static final LawDiffs NO_DIFF = (law, baseline) -> Optional.empty();
    private static final LawDiffs HAS_DIFF = (law, baseline) -> Optional.of(STORED);

    @Test
    @DisplayName("아무것도 없으면 두 차원 모두")
    void 둘_다_없음() {
        assertEquals(Set.of(QueryType.SUMMARY, QueryType.DIFF), LayerATargets.missing(AMENDMENT, BASELINE, NO_SUMMARY, NO_DIFF));
    }

    @Test
    @DisplayName("있는 차원은 빼고, 둘 다 있으면 비어 있다")
    void 있는_차원은_제외() {
        assertEquals(Set.of(QueryType.DIFF), LayerATargets.missing(AMENDMENT, BASELINE, HAS_SUMMARY, NO_DIFF));
        assertEquals(Set.of(QueryType.SUMMARY), LayerATargets.missing(AMENDMENT, BASELINE, NO_SUMMARY, HAS_DIFF));
        assertEquals(Set.of(), LayerATargets.missing(AMENDMENT, BASELINE, HAS_SUMMARY, HAS_DIFF));
    }

    @Test
    @DisplayName("기준선이 없는 개정본은 변경점을 만들 수 없다")
    void 기준선_없는_개정본() {
        assertEquals(Set.of(QueryType.SUMMARY), LayerATargets.missing(AMENDMENT, null, NO_SUMMARY, NO_DIFF));
    }

    @Test
    @DisplayName("제정은 기준선 없이도 변경점 대상이다")
    void 제정() {
        assertEquals(Set.of(QueryType.SUMMARY, QueryType.DIFF), LayerATargets.missing(ENACTMENT, null, NO_SUMMARY, NO_DIFF));
    }
}
