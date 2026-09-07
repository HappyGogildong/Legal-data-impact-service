package com.lia.core.pipeline.dispatch;

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
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.DiscoveryCriteria;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.store.LawSource;

/**
 * QueryDispatcher 단위 — <b>라우팅·부분성공 조립</b>을 FakeHandler·FakeLawSource로 검증.
 * 실제 추론(AnalysisEngine)·DB 없음. 못 채우는 차원은 예외가 아니라 unmet으로 정직하게 표시.
 */
class QueryDispatcherTest {

    // --- fakes / fixtures ------------------------------------------------

    static final class FakeLawSource implements LawSource {
        final Map<String, Law> versions = new HashMap<>();
        final Map<String, Law> baselines = new HashMap<>();
        public Optional<Law> find(String lawId, LocalDate efYd) {
            return Optional.ofNullable(versions.get(lawId + "@" + efYd));
        }
        public Optional<Law> findBaseline(String lawId) {
            return Optional.ofNullable(baselines.get(lawId));
        }
    }

    static DimensionHandler handler(QueryType type, boolean needsProfile, AnalyzeResponse resp) {
        return new DimensionHandler() {
            public QueryType type() { return type; }
            public boolean needsProfile() { return needsProfile; }
            public AnalyzeResponse handle(DispatchContext ctx) { return resp; }
        };
    }

    static AnalyzeResponse resp(String tag) {
        return new AnalyzeResponse(
                new ImpactResult("LAW:001809@2026-08-04", tag, tag,
                        List.of(), List.of(), List.of(), null, List.of(), "참고", null),
                Set.of("LAW:001809@2026-08-04:art:18"));
    }

    static AnalysisQuery refQuery(Set<QueryType> types, QueryType primary, boolean profileBound) {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
        return new AnalysisQuery(primary, types, new Target.Reference(ref),
                "의도", ArticleScope.CHANGED_ONLY, profileBound, null);
    }

    static Law law() { return law(Law.AmendKind.일부개정); }

    static Law law(Law.AmendKind kind) {
        List<Article> arts = List.of(new Article("18", "제목", "제18조 통합심의.",
                true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                kind, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    static DimensionHandler baselineHandler(QueryType type, AnalyzeResponse resp) {
        return new DimensionHandler() {
            public QueryType type() { return type; }
            public boolean needsBaseline() { return true; }
            public AnalyzeResponse handle(DispatchContext ctx) { return resp; }
        };
    }

    private QueryDispatcher dispatcher(FakeLawSource src, DimensionHandler... hs) {
        return new QueryDispatcher(src, new DimensionHandlerRegistry(List.of(hs)));
    }

    private FakeLawSource loaded() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law());
        return src;
    }

    // --- tests -----------------------------------------------------------

    @Test
    void 단일차원_Reference는_filled_1건() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, false, resp("SUMMARY")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.SUMMARY), QueryType.SUMMARY, false));

        assertTrue(r.fullySatisfied());
        assertEquals(1, r.filled().size());
        assertEquals("SUMMARY", r.filled().get(QueryType.SUMMARY).result().command());
    }

    @Test
    void 복수차원_SUMMARY_DIFF는_둘다_filled() {
        QueryDispatcher d = dispatcher(loaded(),
                handler(QueryType.SUMMARY, false, resp("S")),
                handler(QueryType.DIFF, false, resp("D")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.SUMMARY, QueryType.DIFF), QueryType.SUMMARY, false));

        assertTrue(r.fullySatisfied());
        assertEquals(2, r.filled().size());
    }

    @Test
    void 미구현차원_IMPACT는_unmet이고_나머지는_filled() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, false, resp("S")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.SUMMARY, QueryType.IMPACT), QueryType.SUMMARY, false));

        assertEquals(1, r.filled().size());
        assertTrue(r.unmet().containsKey(QueryType.IMPACT));
        assertFalse(r.fullySatisfied());
    }

    @Test
    void 프로필_필요한데_profileBound_false면_unmet() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.IMPACT, true, resp("I")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.IMPACT), QueryType.IMPACT, false));

        assertTrue(r.unmet().get(QueryType.IMPACT).contains("프로필"));
    }

    @Test
    void 프로필_있으면_LayerB_핸들러도_filled() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.IMPACT, true, resp("I")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.IMPACT), QueryType.IMPACT, true));

        assertTrue(r.fullySatisfied());
    }

    @Test
    void 정본_미적재면_전_타입_unmet() {
        QueryDispatcher d = dispatcher(new FakeLawSource(), handler(QueryType.SUMMARY, false, resp("S")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.SUMMARY), QueryType.SUMMARY, false));

        assertTrue(r.filled().isEmpty());
        assertTrue(r.unmet().get(QueryType.SUMMARY).contains("정본 미적재"));
    }

    @Test
    void 개정본인데_baseline이_없으면_baseline_필요_차원은_unmet() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law(Law.AmendKind.일부개정)); // 개정본, baseline 미등록
        QueryDispatcher d = dispatcher(src, baselineHandler(QueryType.DIFF, resp("D")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.DIFF), QueryType.DIFF, false));

        assertTrue(r.filled().isEmpty(), "개정본 baseline 이상은 조용히 제정으로 오인하지 말 것");
        assertTrue(r.unmet().get(QueryType.DIFF).contains("기준선"));
    }

    @Test
    void 제정본은_baseline이_없어도_baseline_필요_차원이_filled() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law(Law.AmendKind.제정)); // 제정 = baseline 원래 없음(정상)
        QueryDispatcher d = dispatcher(src, baselineHandler(QueryType.DIFF, resp("D")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.DIFF), QueryType.DIFF, false));

        assertTrue(r.fullySatisfied(), "제정본은 baseline null이 정상 → 진행");
    }

    @Test
    void Discovery_타겟은_현재_전부_unmet() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, false, resp("S")));
        AnalysisQuery q = new AnalysisQuery(QueryType.LOOKUP, Set.of(QueryType.LOOKUP),
                new Target.Discovery(new DiscoveryCriteria(List.of("전세"), List.of(), List.of(), false)),
                "찾아줘", ArticleScope.CHANGED_ONLY, false, null);

        DispatchResult r = d.dispatch(q);

        assertTrue(r.filled().isEmpty());
        assertTrue(r.unmet().containsKey(QueryType.LOOKUP));
    }
}
