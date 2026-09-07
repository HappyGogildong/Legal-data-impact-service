package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.analyze.Reasoner;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.dispatch.DiffHandler;
import com.lia.core.pipeline.dispatch.DimensionHandlerRegistry;
import com.lia.core.pipeline.dispatch.QueryDispatcher;
import com.lia.core.pipeline.dispatch.SummaryHandler;
import com.lia.core.pipeline.plan.AnalysisQueryDraft;
import com.lia.core.pipeline.plan.AnalysisQueryDraft.TargetKind;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.QueryTranslator;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.resolve.LawLookup;
import com.lia.core.pipeline.resolve.ResolutionState;
import com.lia.core.pipeline.resolve.SourceAnalyzer;
import com.lia.core.store.LawSource;

/**
 * AnalysisService 단위 — 실 {@link QueryPlanner}+{@link QueryDispatcher} 체인을 기존 Fake(번역·해소·정본·추론)로
 * 조립해 <b>in-JVM 관통</b>을 본다. 실 LLM·DB 없음. plan→dispatch 글루와 4상태/부분성공 흐름 검증.
 */
class AnalysisServiceTest {

    /** "주택법"만 해소되는 Fake 코퍼스(QueryPlannerTest와 동일 패턴). */
    static class FakeLookup implements LawLookup {
        @Override public List<RawLaw> searchByName(String query, int limit) {
            if (query != null && query.contains("주택법")) {
                return List.of(new RawLaw("001809", "283191", "주택법", "시행예정",
                        LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", null));
            }
            return List.of();
        }
    }

    static final class FakeLawSource implements LawSource {
        final Map<String, Law> versions = new HashMap<>();
        public Optional<Law> find(String lawId, LocalDate efYd) { return Optional.ofNullable(versions.get(lawId + "@" + efYd)); }
        public Optional<Law> findBaseline(String lawId) { return Optional.empty(); }
    }

    /** 주입된 source_id를 인용해 항상 그라운딩 통과하는 Fake 추론기. */
    static Reasoner groundedReasoner() {
        return ctx -> {
            String cite = ctx.injectedSourceIds().iterator().next();
            return new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "주택법 개정 요약",
                    List.of(new ImpactResult.Claim("주택법이 개정된다", List.of(cite), 0.9)),
                    List.of(), List.of(), null, List.of(), "참고용", null);
        };
    }

    static Law law() {
        List<Article> arts = List.of(new Article("18", "제목", "제18조 통합심의.",
                true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    private AnalysisService service(AnalysisQueryDraft draft, boolean lawLoaded) {
        QueryTranslator translator = q -> draft;
        QueryPlanner planner = new QueryPlanner(translator, new SourceAnalyzer(new FakeLookup()));
        FakeLawSource src = new FakeLawSource();
        if (lawLoaded) src.versions.put("001809@2026-08-04", law());
        AnalysisEngine engine = new AnalysisEngine(new ContextBuilder(), groundedReasoner(), 3);
        DimensionHandlerRegistry registry =
                new DimensionHandlerRegistry(List.of(new SummaryHandler(engine), new DiffHandler(engine)));
        return new AnalysisService(planner, new QueryDispatcher(src, registry));
    }

    @Test
    void 해소되는_질의는_Analyzed로_SUMMARY가_채워진다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "요약");

        AnalysisOutcome out = service(draft, true).analyze("주택법 뭐가 바뀌어?", null);

        AnalysisOutcome.Analyzed a = assertInstanceOf(AnalysisOutcome.Analyzed.class, out);
        assertTrue(a.result().filled().containsKey(QueryType.SUMMARY), "SUMMARY 차원이 채워져야");
        assertTrue(a.result().fullySatisfied(), "SUMMARY 단일 질의는 완전 충족");
    }

    @Test
    void 미해소_비법령_질의는_Unresolved로_분석되지_않는다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), false, TargetKind.REFERENCE,
                null, null, List.of(), List.of(), List.of(), "점심 메뉴");

        AnalysisOutcome out = service(draft, false).analyze("오늘 점심 뭐 먹지", null);

        AnalysisOutcome.Unresolved u = assertInstanceOf(AnalysisOutcome.Unresolved.class, out);
        assertEquals(ResolutionState.UNVERIFIED, u.resolution().state());
    }
}
