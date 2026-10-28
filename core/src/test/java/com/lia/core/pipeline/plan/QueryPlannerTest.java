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
            if (query != null && query.contains("자본시장")) {   // 같은 법령의 시행예정본 2개(D43)
                return List.of(
                        new RawLaw("010513", "283193", "자본시장과 금융투자업에 관한 법률", "시행예정",
                                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 4, 1), "21000", null),
                        new RawLaw("010513", "285001", "자본시장과 금융투자업에 관한 법률", "시행예정",
                                LocalDate.of(2026, 11, 13), LocalDate.of(2026, 5, 13), "21100", null));
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
    void 프로필_없이_LayerB만_있으면_선계산_요약을_더해_보여준다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.IMPACT, Set.of(QueryType.IMPACT, QueryType.ACTION), true,
                TargetKind.REFERENCE, "주택법", null, List.of(), List.of(), List.of(), "전세 영향");

        PlanResult result = plannerFor(draft).plan("주택법 바뀌면 나 어떻게 돼", null, false);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        assertEquals(Set.of(QueryType.SUMMARY, QueryType.IMPACT, QueryType.ACTION), q.types());
        assertEquals(QueryType.IMPACT, q.primaryType(), "주 차원은 질문이 정한 그대로");
        assertFalse(q.profileBound());
    }

    @Test
    void 프로필이_있으면_요약을_더하지_않는다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.IMPACT, Set.of(QueryType.IMPACT), true,
                TargetKind.REFERENCE, "주택법", null, List.of(), List.of(), List.of(), "전세 영향");

        PlanResult result = plannerFor(draft).plan("주택법 바뀌면 나 어떻게 돼", null, true);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        assertEquals(Set.of(QueryType.IMPACT), q.types());
        assertTrue(q.profileBound());
    }

    @Test
    void 프로필이_없어도_LayerA_차원이_이미_있으면_요약을_더하지_않는다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.IMPACT, Set.of(QueryType.DIFF, QueryType.IMPACT), true,
                TargetKind.REFERENCE, "주택법", null, List.of(), List.of(), List.of(), "전세 영향");

        PlanResult result = plannerFor(draft).plan("주택법 바뀐 점이랑 내 영향", null, false);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        assertEquals(Set.of(QueryType.DIFF, QueryType.IMPACT), q.types());
    }

    @Test
    void 프로필이_없어도_LayerB_차원을_유지한다_미충족_안내가_응답에_실리게() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.IMPACT, Set.of(QueryType.DIFF, QueryType.IMPACT, QueryType.ACTION), true,
                TargetKind.REFERENCE, "주택법", null, List.of(), List.of(), List.of(), "전세 영향");

        PlanResult result = plannerFor(draft).plan("주택법 바뀌면 나 전세 어떻게 돼", null, false);

        AnalysisQuery q = assertInstanceOf(PlanResult.Planned.class, result).query();
        assertEquals(Set.of(QueryType.DIFF, QueryType.IMPACT, QueryType.ACTION), q.types());
        assertEquals(QueryType.IMPACT, q.primaryType());
        assertFalse(q.profileBound(), "프로필 없으면 profileBound=false (채울 수는 없다)");
    }

    @Test
    void 같은_법령의_다른_시행예정본은_alternatives로_전달된다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "자본시장과 금융투자업에 관한 법률", null, List.of(), List.of(), List.of(), "요약");

        PlanResult.Planned planned = assertInstanceOf(PlanResult.Planned.class,
                plannerFor(draft).plan("자본시장법 뭐가 바뀌어?", null, false));

        Target.Reference ref = assertInstanceOf(Target.Reference.class, planned.query().target());
        assertEquals(LocalDate.of(2026, 10, 1), ref.lawRef().effectiveDate(), "가장 이른 시행일로 해소(D43)");
        assertEquals(List.of(LocalDate.of(2026, 11, 13)),
                planned.alternatives().stream().map(RawLaw::effectiveDate).toList());
    }

    @Test
    void 명시적_참조면_alternatives는_비어_있다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "요약");

        PlanResult.Planned planned = assertInstanceOf(PlanResult.Planned.class, plannerFor(draft)
                .plan("요약", new LawRef("001809", LocalDate.of(2026, 8, 4), null), false));

        assertTrue(planned.alternatives().isEmpty());
    }
}
