package com.lia.core.application.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.discover.DiscoveredLaw;
import com.lia.core.pipeline.plan.DiscoveryCriteria;
import com.lia.core.pipeline.discover.DiscoveryResult;
import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;

/** 유스케이스 결과 → 질의 이벤트(D83). 원문·식별자 없이 결과의 모양만. */
class QueryEventsTest {

    private static final Instant AT = Instant.parse("2026-11-11T01:00:00Z");
    private static final AnalyzeResponse ANSWER = new AnalyzeResponse(new ImpactResult("LAW:x", "SUMMARY", "요약",
            List.of(), List.of(), List.of(), null, List.of(), "참고", null), Set.of());

    private static AnalysisQuery reference(String articleNo, QueryType primary, QueryType... types) {
        return new AnalysisQuery(primary, Set.of(types),
                new Target.Reference(new LawRef("001809", LocalDate.of(2026, 8, 4), articleNo)), "의도", null, false, null);
    }

    @Test
    void 법령_지목_분석은_채운_차원과_못_채운_사유를_남긴다() {
        AnalysisOutcome outcome = new AnalysisOutcome.Analyzed(reference("20", QueryType.DIFF, QueryType.SUMMARY, QueryType.DIFF),
                new DispatchResult(QueryType.DIFF, Map.of(QueryType.DIFF, ANSWER),
                        Map.of(QueryType.SUMMARY, new Unmet(Unmet.Reason.NOT_PRECOMPUTED, "저장본 없음"))));

        QueryEvent event = QueryEvents.analyzed(AT, true, false, "0.3", outcome);

        assertEquals(QueryEvent.Kind.ANALYZE, event.kind());
        assertEquals(QueryEvent.Status.ANSWERED, event.status());
        assertTrue(event.signedIn());
        assertEquals(QueryEvent.Route.REFERENCE, event.route());
        assertNull(event.resolutionState());
        assertEquals(QueryType.DIFF, event.primaryType());
        assertEquals(Set.of(QueryType.SUMMARY, QueryType.DIFF), event.types());
        assertEquals(Set.of(QueryType.DIFF), event.filled());
        assertEquals(Map.of(QueryType.SUMMARY, Unmet.Reason.NOT_PRECOMPUTED), event.unmet());
        assertEquals(Boolean.TRUE, event.articleNamed());
        assertNull(event.candidates());
        assertEquals(Boolean.FALSE, event.profileSearch());
        assertEquals("0.3", event.promptVersion());
    }

    @Test
    void 아무것도_못_채우면_NOT_ANSWERED() {
        AnalysisOutcome outcome = new AnalysisOutcome.Analyzed(reference(null, QueryType.SUMMARY, QueryType.SUMMARY),
                new DispatchResult(QueryType.SUMMARY, Map.of(),
                        Map.of(QueryType.SUMMARY, new Unmet(Unmet.Reason.LAW_NOT_LOADED, "정본 없음"))));

        QueryEvent event = QueryEvents.analyzed(AT, false, false, "0.3", outcome);

        assertEquals(QueryEvent.Status.NOT_ANSWERED, event.status());
        assertEquals(Boolean.FALSE, event.articleNamed());
        assertEquals(Map.of(QueryType.SUMMARY, Unmet.Reason.LAW_NOT_LOADED), event.unmet());
    }

    private static AnalysisOutcome discovery(int candidates) {
        AnalysisQuery query = new AnalysisQuery(QueryType.LOOKUP, Set.of(QueryType.LOOKUP),
                new Target.Discovery(new DiscoveryCriteria(List.of("임도"), List.of(), List.of(), false)),
                "의도", null, false, null);
        List<DiscoveredLaw> found = java.util.stream.IntStream.range(0, candidates)
                .mapToObj(i -> new DiscoveredLaw("01512" + i, "법", LocalDate.of(2027, 4, 1), "산림청", null, false, null))
                .toList();
        return new AnalysisOutcome.Analyzed(query, new DispatchResult(QueryType.LOOKUP, Map.of(), Map.of(),
                new DispatchResult.Discovery(new DiscoveryResult(found, false), null)));
    }

    @Test
    void 발견은_후보_수를_남기고_후보가_있으면_ANSWERED() {
        QueryEvent event = QueryEvents.analyzed(AT, false, true, "0.3", discovery(3));

        assertEquals(QueryEvent.Route.DISCOVERY, event.route());
        assertEquals(QueryEvent.Status.ANSWERED, event.status());
        assertEquals(3, event.candidates());
        assertNull(event.articleNamed());
        assertEquals(Boolean.TRUE, event.profileSearch());
    }

    @Test
    void 발견_후보가_없으면_NOT_ANSWERED() {
        QueryEvent event = QueryEvents.analyzed(AT, false, false, "0.3", discovery(0));

        assertEquals(QueryEvent.Status.NOT_ANSWERED, event.status());
        assertEquals(0, event.candidates());
    }

    @Test
    void 비슷한_이름_후보는_AMBIGUOUS_와_후보_수() {
        RawLaw a = new RawLaw("1", "m", "주택법", "시행예정", LocalDate.of(2027, 1, 1), null, null, null);
        RawLaw b = new RawLaw("2", "m", "주택임대차보호법", "시행예정", LocalDate.of(2027, 1, 1), null, null, null);
        QueryEvent event = QueryEvents.analyzed(AT, false, false, "0.3",
                new AnalysisOutcome.Unresolved(ResolutionResult.ambiguous(List.of(a, b), "어느 법인가요?")));

        assertEquals(QueryEvent.Status.UNRESOLVED, event.status());
        assertEquals("AMBIGUOUS", event.resolutionState());
        assertEquals(2, event.candidates());
        assertNull(event.route());
        assertTrue(event.types().isEmpty());
    }

    @Test
    void 법령_질문이_아니면_OFF_TOPIC() {
        QueryEvent event = QueryEvents.analyzed(AT, false, false, "0.3",
                new AnalysisOutcome.Unresolved(ResolutionResult.unverified(List.of(), "법령에 관한 질의가 아닙니다."), true));

        assertEquals(QueryEvent.OFF_TOPIC, event.resolutionState());
        assertNull(event.candidates());
    }

    @Test
    void 없는_법_이름은_UNVERIFIED() {
        QueryEvent event = QueryEvents.analyzed(AT, false, false, "0.3",
                new AnalysisOutcome.Unresolved(ResolutionResult.unverified(List.of(), "그런 법은 없어요"), false));

        assertEquals("UNVERIFIED", event.resolutionState());
    }

    @Test
    void 익명_상한은_분류_전이라_결과_모양이_없다() {
        QueryEvent event = QueryEvents.anonymousLimit(AT, false, "0.3");

        assertEquals(QueryEvent.Status.ANONYMOUS_LIMIT, event.status());
        assertFalse(event.signedIn());
        assertNull(event.route());
        assertNull(event.primaryType());
    }

    @Test
    void 재생성이_되면_ACTION_을_채운다() {
        QueryEvent event = QueryEvents.revision(AT, true, "0.3", new RevisionOutcome.Revised(ANSWER));

        assertEquals(QueryEvent.Kind.ACTION_REVISION, event.kind());
        assertEquals(QueryEvent.Status.ANSWERED, event.status());
        assertEquals(QueryEvent.Route.REFERENCE, event.route());
        assertEquals(QueryType.ACTION, event.primaryType());
        assertEquals(Set.of(QueryType.ACTION), event.types());
        assertEquals(Set.of(QueryType.ACTION), event.filled());
        assertNull(event.profileSearch());
    }

    @Test
    void 재생성을_못_하면_사유를_ACTION_에_남긴다() {
        QueryEvent event = QueryEvents.revision(AT, true, "0.3",
                new RevisionOutcome.NotRevised(new Unmet(Unmet.Reason.DAILY_LIMIT, "한도")));

        assertEquals(QueryEvent.Status.NOT_ANSWERED, event.status());
        assertTrue(event.filled().isEmpty());
        assertEquals(Map.of(QueryType.ACTION, Unmet.Reason.DAILY_LIMIT), event.unmet());
    }
}
