package com.lia.core.pipeline.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.plan.AnalysisQueryDraft.TargetKind;
import com.lia.core.pipeline.resolve.LawLookup;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.pipeline.resolve.ResolutionState;
import com.lia.core.pipeline.resolve.SourceAnalyzer;

/**
 * QueryPlanner 단위 — 번역기(Fake)로 draft를 통제하고, 번역 이후의 <b>결정론 로직</b>
 * (해소 분기·프로필 게이팅·fail-closed)을 검증한다. 실 LLM 없음.
 */
class QueryPlannerTest {

    /** 이름으로 통제하는 Fake 코퍼스 — "주택법" 만 후보 반환. */
    static class FakeLookup implements LawLookup {
        @Override public List<RawLaw> searchByName(String query, int limit) {
            if (query != null && query.contains("주택법")) {
                return List.of(new RawLaw("001809", "283191", "주택법", "시행예정",
                        LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", null));
            }
            return List.of();
        }
    }

    private QueryPlanner plannerFor(AnalysisQueryDraft draft) {
        QueryTranslator translator = query -> draft;
        return new QueryPlanner(translator, new SourceAnalyzer(new FakeLookup()));
    }

    @Test
    void reference_해소되면_Planned_Reference() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.DIFF, Set.of(QueryType.DIFF), true, TargetKind.REFERENCE,
                "주택법", "18", List.of(), List.of(), List.of(), "제18조 변경점");

        PlanResult result = plannerFor(draft).plan("주택법 제18조 뭐가 바뀌었어?", null, false);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        assertEquals(QueryType.DIFF, q.primaryType());
        Target.Reference ref = assertInstanceOf(Target.Reference.class, q.target());
        assertEquals("001809", ref.lawRef().lawId());
        assertEquals(LocalDate.of(2026, 8, 4), ref.lawRef().effectiveDate());
        assertEquals("18", ref.lawRef().articleNo());
    }

    @Test
    void 비법령_질의는_UNVERIFIED로_조기거부() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), false, TargetKind.REFERENCE,
                null, null, List.of(), List.of(), List.of(), "점심 메뉴");

        PlanResult result = plannerFor(draft).plan("오늘 점심 뭐 먹지", null, false);

        ResolutionResult rr = assertInstanceOf(PlanResult.Unresolved.class, result).resolution();
        assertEquals(ResolutionState.UNVERIFIED, rr.state(), "비법령은 UNVERIFIED");
    }

    @Test
    void discovery_질의는_해소없이_Planned_criteria() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.LOOKUP, Set.of(QueryType.LOOKUP), true, TargetKind.DISCOVERY,
                null, null, List.of("전세", "임대차"), List.of(), List.of("주거"), "영향 있을 법 찾기");

        PlanResult result = plannerFor(draft).plan("나한테 영향 있을 시행예정 법령 찾아줘", null, true);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        Target.Discovery disc = assertInstanceOf(Target.Discovery.class, q.target());
        assertTrue(disc.criteria().keywords().contains("전세"));
        assertTrue(disc.criteria().profileBound(), "프로필 있으면 profileBound");
        assertEquals(QueryType.LOOKUP, q.primaryType());
    }

    @Test
    void 프로필_없으면_LayerB_제거_primary도_교체() {
        // S1류 포괄질의지만 프로필 미제공 → IMPACT·ACTION 제거, DIFF만 남음
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.IMPACT, Set.of(QueryType.DIFF, QueryType.IMPACT, QueryType.ACTION), true,
                TargetKind.REFERENCE, "주택법", null, List.of(), List.of(), List.of(), "전세 영향");

        PlanResult result = plannerFor(draft).plan("주택법 바뀌면 나 전세 어떻게 돼", null, false);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        assertFalse(q.profileBound(), "프로필 없으면 profileBound=false");
        assertFalse(q.types().contains(QueryType.IMPACT), "IMPACT 제거");
        assertFalse(q.types().contains(QueryType.ACTION), "ACTION 제거");
        assertTrue(q.types().contains(QueryType.DIFF), "Layer A는 유지");
        assertFalse(q.primaryType().isLayerB(), "primary가 Layer B면 교체되어야");
    }
}
