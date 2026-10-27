package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.store.LawSummaryStore;
import com.lia.core.testsupport.TestLaws;

/** 저장된 요약이 지금 조립되는 근거 집합 안에서만 인용하는지 — DB 없이 저장소를 대체해 확인한다. */
class GroundedSummariesTest {

    private static final Law LAW = TestLaws.law("A", "M1", Law.Status.시행예정, LocalDate.of(2027, 1, 1));

    /** 저장소 조회만 고정값으로 바꾼 저장소 — DB 접근 없음. */
    private static LawSummaryStore storeReturning(ImpactResult stored) {
        return new LawSummaryStore(null, null, "p1") {
            @Override
            public Optional<ImpactResult> findStored(Law law) {
                return Optional.ofNullable(stored);
            }
        };
    }

    private static ImpactResult citing(List<ImpactResult.Claim> claims, List<ImpactResult.Impact> impacts,
                                       List<ImpactResult.Action> actions) {
        return new ImpactResult(LAW.ref(), "SUMMARY", "요약", claims, impacts, actions, null, List.of(), "참고용", null);
    }

    private static GroundedSummaries summariesOf(ImpactResult stored) {
        return new GroundedSummaries(storeReturning(stored), new ContextBuilder());
    }

    @Test
    @DisplayName("모든 인용이 지금 근거 집합 안이면 저장본을 돌려준다")
    void 근거_안이면_있음() {
        String inside = LAW.amendSourceId();
        ImpactResult stored = citing(
                List.of(new ImpactResult.Claim("주장", List.of(inside), 0.9)),
                List.of(new ImpactResult.Impact("비용", "증가", "상세", List.of(inside))),
                List.of(new ImpactResult.Action("신고", "2027-01-01", List.of(inside))));

        assertSame(stored, summariesOf(stored).findCurrent(LAW).orElseThrow());
    }

    @Test
    @DisplayName("claims 가 지금 근거 밖 source_id 를 인용하면 empty")
    void 주장_인용이_근거_밖이면_없음() {
        ImpactResult stored = citing(
                List.of(new ImpactResult.Claim("주장", List.of("LAW:A@2027-01-01:art:옛형식"), 0.9)), List.of(), List.of());

        assertTrue(summariesOf(stored).findCurrent(LAW).isEmpty());
    }

    @Test
    @DisplayName("impacts·actions 의 인용이 근거 밖이어도 empty — 엔진의 인용 검증과 같은 범위")
    void 영향과_대응_인용도_본다() {
        String inside = LAW.amendSourceId();
        ImpactResult outsideImpact = citing(
                List.of(new ImpactResult.Claim("주장", List.of(inside), 0.9)),
                List.of(new ImpactResult.Impact("비용", "증가", "상세", List.of("LAW:없음"))), List.of());
        ImpactResult outsideAction = citing(
                List.of(new ImpactResult.Claim("주장", List.of(inside), 0.9)),
                List.of(), List.of(new ImpactResult.Action("신고", null, List.of("LAW:없음"))));

        assertTrue(summariesOf(outsideImpact).findCurrent(LAW).isEmpty());
        assertTrue(summariesOf(outsideAction).findCurrent(LAW).isEmpty());
    }

    @Test
    @DisplayName("저장본이 없으면 empty")
    void 저장본_없음() {
        assertTrue(summariesOf(null).findCurrent(LAW).isEmpty());
    }
}
