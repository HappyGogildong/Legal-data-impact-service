package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.InsufficientGroundingException;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.DiscoveryCriteria;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.profile.UserProfile;
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

    static DimensionHandler handler(QueryType type, AnalyzeResponse resp) {
        return new DimensionHandler() {
            public QueryType type() { return type; }
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

    /** 호출 횟수를 세는 Layer B 핸들러 — 엔진 대신 고정 결과. */
    static final class CountingPersonalized extends PersonalizedHandler {
        int calls;
        RuntimeException failure;
        CountingPersonalized() { super(null); }
        @Override
        public Map<QueryType, AnalyzeResponse> handle(DispatchContext ctx, UserProfile profile, String question,
                                                      Set<QueryType> requested) {
            calls++;
            if (failure != null) throw failure;
            Map<QueryType, AnalyzeResponse> out = new java.util.EnumMap<>(QueryType.class);
            for (QueryType t : requested) out.put(t, resp(t.name()));
            return out;
        }
    }

    static final class FakeQuota implements PersonalizationQuota {
        boolean allow = true;
        int consumed;
        public boolean tryConsume(UUID userId) {
            if (!allow) return false;
            consumed++;
            return true;
        }
    }

    final CountingPersonalized personalized = new CountingPersonalized();
    final FakeQuota quota = new FakeQuota();

    static final Personalization USABLE = new Personalization.Usable(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            new UserProfile(null, 34, null, null, null, null, null));

    private QueryDispatcher dispatcher(FakeLawSource src, DimensionHandler... hs) {
        return new QueryDispatcher(src, new DimensionHandlerRegistry(List.of(hs)), personalized, quota);
    }

    private static DispatchResult dispatchAnonymous(QueryDispatcher d, AnalysisQuery q) {
        return d.dispatch(q, "질문", Personalization.anonymous());
    }

    private FakeLawSource loaded() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law());
        return src;
    }

    /** 일부개정본 + 시행중 기준선 — Layer B 가 BASELINE_NOT_LOADED 로 막히지 않는 정상 상태. */
    private FakeLawSource loadedWithBaseline() {
        FakeLawSource src = loaded();
        src.baselines.put("001809", law());
        return src;
    }

    // --- tests -----------------------------------------------------------

    @Test
    void 단일차원_Reference는_filled_1건() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, resp("SUMMARY")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.SUMMARY), QueryType.SUMMARY, false));

        assertTrue(r.fullySatisfied());
        assertEquals(1, r.filled().size());
        assertEquals("SUMMARY", r.filled().get(QueryType.SUMMARY).result().command());
    }

    @Test
    void 복수차원_SUMMARY_DIFF는_둘다_filled() {
        QueryDispatcher d = dispatcher(loaded(),
                handler(QueryType.SUMMARY, resp("S")),
                handler(QueryType.DIFF, resp("D")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.SUMMARY, QueryType.DIFF), QueryType.SUMMARY, false));

        assertTrue(r.fullySatisfied());
        assertEquals(2, r.filled().size());
    }

    @Test
    void 미구현차원_DIFF는_unmet이고_나머지는_filled() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, resp("S")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.SUMMARY, QueryType.DIFF), QueryType.SUMMARY, false));

        assertEquals(1, r.filled().size());
        assertEquals(Unmet.Reason.NOT_IMPLEMENTED, r.unmet().get(QueryType.DIFF).reason());
        assertFalse(r.fullySatisfied());
    }

    @Test
    void LayerB_두_차원은_핸들러_한_번으로_채우고_한도를_한_번_차감한다() {
        QueryDispatcher d = dispatcher(loadedWithBaseline(), handler(QueryType.SUMMARY, resp("S")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.SUMMARY, QueryType.IMPACT, QueryType.ACTION),
                QueryType.IMPACT, true), "주택법 내 영향", USABLE);

        assertEquals(1, personalized.calls);
        assertEquals(1, quota.consumed);
        assertTrue(r.filled().keySet().containsAll(Set.of(QueryType.SUMMARY, QueryType.IMPACT, QueryType.ACTION)));
        assertTrue(r.unmet().isEmpty());
    }

    @Test
    void 프로필을_못_쓰면_LayerB_는_그_사유로_unmet_한도도_핸들러도_안_건드린다() {
        QueryDispatcher d = dispatcher(loadedWithBaseline(), handler(QueryType.SUMMARY, resp("S")));

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.SUMMARY, QueryType.IMPACT), QueryType.SUMMARY, false),
                "질문", new Personalization.Blocked(Unmet.Reason.CONSENT_OUTDATED));

        assertEquals(Unmet.Reason.CONSENT_OUTDATED, r.unmet().get(QueryType.IMPACT).reason());
        assertTrue(r.filled().containsKey(QueryType.SUMMARY));
        assertEquals(0, personalized.calls);
        assertEquals(0, quota.consumed);
    }

    @Test
    void 한도를_넘으면_DAILY_LIMIT_핸들러는_안_부른다() {
        quota.allow = false;
        QueryDispatcher d = dispatcher(loadedWithBaseline());

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.IMPACT, QueryType.ACTION), QueryType.IMPACT, true),
                "질문", USABLE);

        assertEquals(Unmet.Reason.DAILY_LIMIT, r.unmet().get(QueryType.IMPACT).reason());
        assertEquals(Unmet.Reason.DAILY_LIMIT, r.unmet().get(QueryType.ACTION).reason());
        assertEquals(0, personalized.calls);
    }

    @Test
    void 개정본인데_기준선이_없으면_LayerB_는_BASELINE_NOT_LOADED_한도_차감_없음() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law());   // baseline 없음, 일부개정
        QueryDispatcher d = dispatcher(src);

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.IMPACT), QueryType.IMPACT, true), "질문", USABLE);

        assertEquals(Unmet.Reason.BASELINE_NOT_LOADED, r.unmet().get(QueryType.IMPACT).reason());
        assertEquals(0, quota.consumed);
    }

    @Test
    void 제정본은_기준선이_없어도_LayerB_가_핸들러에_닿는다() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law(Law.AmendKind.제정));   // baseline 없음 — 제정이라 정상
        QueryDispatcher d = dispatcher(src);

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.IMPACT, QueryType.ACTION), QueryType.IMPACT, true),
                "질문", USABLE);

        assertEquals(1, personalized.calls);
        assertEquals(1, quota.consumed);
        assertTrue(r.filled().keySet().containsAll(Set.of(QueryType.IMPACT, QueryType.ACTION)));
        assertTrue(r.unmet().isEmpty());
    }

    @Test
    void LayerB_근거부족이면_요청한_차원_모두_INSUFFICIENT_GROUNDING() {
        personalized.failure = new InsufficientGroundingException("근거 부족");
        QueryDispatcher d = dispatcher(loadedWithBaseline());

        DispatchResult r = d.dispatch(refQuery(Set.of(QueryType.IMPACT, QueryType.ACTION), QueryType.IMPACT, true),
                "질문", USABLE);

        assertEquals(Unmet.Reason.INSUFFICIENT_GROUNDING, r.unmet().get(QueryType.IMPACT).reason());
        assertEquals(Unmet.Reason.INSUFFICIENT_GROUNDING, r.unmet().get(QueryType.ACTION).reason());
        assertEquals(1, quota.consumed, "호출 직전 차감 — 실패해도 환불 없음");
    }

    @Test
    void 익명이면_LayerB_는_PROFILE_REQUIRED_나머지는_filled() {
        QueryDispatcher d = dispatcher(loadedWithBaseline(), handler(QueryType.SUMMARY, resp("S")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.SUMMARY, QueryType.IMPACT), QueryType.SUMMARY, false));

        assertTrue(r.filled().containsKey(QueryType.SUMMARY));
        assertEquals(Unmet.Reason.PROFILE_REQUIRED, r.unmet().get(QueryType.IMPACT).reason());
        assertEquals(0, personalized.calls);
    }

    @Test
    void 정본_미적재면_전_타입_unmet() {
        QueryDispatcher d = dispatcher(new FakeLawSource(), handler(QueryType.SUMMARY, resp("S")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.SUMMARY), QueryType.SUMMARY, false));

        assertTrue(r.filled().isEmpty());
        assertEquals(Unmet.Reason.LAW_NOT_LOADED, r.unmet().get(QueryType.SUMMARY).reason());
        assertTrue(r.unmet().get(QueryType.SUMMARY).message().contains("정본 미적재"), "설명은 사람이 읽는 문장 그대로");
    }

    @Test
    void 개정본인데_baseline이_없으면_baseline_필요_차원은_unmet() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law(Law.AmendKind.일부개정)); // 개정본, baseline 미등록
        QueryDispatcher d = dispatcher(src, baselineHandler(QueryType.DIFF, resp("D")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.DIFF), QueryType.DIFF, false));

        assertTrue(r.filled().isEmpty(), "개정본 baseline 이상은 조용히 제정으로 오인하지 말 것");
        assertEquals(Unmet.Reason.BASELINE_NOT_LOADED, r.unmet().get(QueryType.DIFF).reason());
    }

    @Test
    void 제정본은_baseline이_없어도_baseline_필요_차원이_filled() {
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law(Law.AmendKind.제정)); // 제정 = baseline 원래 없음(정상)
        QueryDispatcher d = dispatcher(src, baselineHandler(QueryType.DIFF, resp("D")));

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.DIFF), QueryType.DIFF, false));

        assertTrue(r.fullySatisfied(), "제정본은 baseline null이 정상 → 진행");
    }

    @Test
    void Discovery_타겟은_현재_전부_unmet() {
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, resp("S")));
        AnalysisQuery q = new AnalysisQuery(QueryType.LOOKUP, Set.of(QueryType.LOOKUP),
                new Target.Discovery(new DiscoveryCriteria(List.of("전세"), List.of(), List.of(), false)),
                "찾아줘", ArticleScope.CHANGED_ONLY, false, null);

        DispatchResult r = dispatchAnonymous(d, q);

        assertTrue(r.filled().isEmpty());
        assertEquals(Unmet.Reason.NOT_IMPLEMENTED, r.unmet().get(QueryType.LOOKUP).reason());
    }

    @Test
    void 핸들러가_근거부족이면_그_차원만_INSUFFICIENT_GROUNDING_나머지는_filled() {
        DimensionHandler failing = new DimensionHandler() {
            public QueryType type() { return QueryType.DIFF; }
            public AnalyzeResponse handle(DispatchContext ctx) {
                throw new InsufficientGroundingException("근거 부족");
            }
        };
        QueryDispatcher d = dispatcher(loaded(), handler(QueryType.SUMMARY, resp("S")), failing);

        DispatchResult r = dispatchAnonymous(d, refQuery(Set.of(QueryType.SUMMARY, QueryType.DIFF), QueryType.SUMMARY, false));

        assertTrue(r.filled().containsKey(QueryType.SUMMARY));
        assertEquals(Unmet.Reason.INSUFFICIENT_GROUNDING, r.unmet().get(QueryType.DIFF).reason());
    }
}
