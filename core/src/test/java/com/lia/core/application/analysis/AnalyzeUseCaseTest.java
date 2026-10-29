package com.lia.core.application.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lia.core.application.profile.PersonalizationGate;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.analyze.Reasoner;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.discover.DiscoveryResult;
import com.lia.core.pipeline.discover.LawDiscovery;
import com.lia.core.pipeline.dispatch.DiffHandler;
import com.lia.core.pipeline.dispatch.DimensionHandlerRegistry;
import com.lia.core.pipeline.dispatch.PersonalizedHandler;
import com.lia.core.pipeline.dispatch.QueryDispatcher;
import com.lia.core.pipeline.dispatch.SummaryHandler;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.AnalysisQueryDraft;
import com.lia.core.pipeline.plan.AnalysisQueryDraft.TargetKind;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.QueryTranslator;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.LawLookup;
import com.lia.core.pipeline.resolve.ResolutionState;
import com.lia.core.pipeline.resolve.SourceAnalyzer;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;
import com.lia.core.profile.UserProfileStore;
import com.lia.core.store.LawSource;

/**
 * AnalyzeUseCase 단위 — 실 {@link QueryPlanner}+{@link QueryDispatcher} 체인을 기존 Fake(번역·해소·정본·추론)로
 * 조립해 <b>in-JVM 관통</b>을 본다. 실 LLM·DB 없음. plan→dispatch 글루와 4상태/부분성공·explicitRef 분기 검증.
 */
class AnalyzeUseCaseTest {

    final List<String> recorded = new ArrayList<>();

    /** 발견 경로를 쓰지 않는 테스트용 — 호출되면 빈 결과. */
    static final LawDiscovery NO_DISCOVERY = new LawDiscovery(null, null, null,
            new LawDiscovery.Settings(40, 10, 0.3, 80, Set.of(Law.Status.시행예정))) {
        @Override
        public DiscoveryResult discover(String question, Optional<UserProfile> profile) {
            return new DiscoveryResult(List.of(), profile.isPresent());
        }
    };

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

    private AnalyzeUseCase useCase(AnalysisQueryDraft draft, boolean lawLoaded) {
        QueryTranslator translator = q -> draft;
        QueryPlanner planner = new QueryPlanner(translator, new SourceAnalyzer(new FakeLookup()));
        FakeLawSource src = new FakeLawSource();
        if (lawLoaded) src.versions.put("001809@2026-08-04", law());
        AnalysisEngine engine = new AnalysisEngine(new ContextBuilder(), groundedReasoner(), 3);
        DimensionHandlerRegistry registry =
                new DimensionHandlerRegistry(List.of(new SummaryHandler(engine, law -> Optional.empty(), new ContextBuilder()), new DiffHandler(engine)));
        return new AnalyzeUseCase(planner, new QueryDispatcher(src, registry, new PersonalizedHandler(null), userId -> false, NO_DISCOVERY),
                (lawId, efYd) -> { recorded.add(lawId + "@" + efYd); return true; }, anonymousGate());
    }

    /** 저장소가 항상 같은 프로필 행(없으면 null)을 돌려주는 게이트 — DB 없이. */
    private static PersonalizationGate gate(StoredProfile stored, String currentPolicyVersion) {
        UserProfileStore store = new UserProfileStore(null) {
            @Override public Optional<StoredProfile> find(UUID userId) { return Optional.ofNullable(stored); }
        };
        return new PersonalizationGate(store, currentPolicyVersion);
    }

    private static PersonalizationGate anonymousGate() { return gate(null, "v1"); }

    /** 번역기가 IMPACT 를 고르고, 게이트 저장소엔 storedPolicyVersion 동의의 채워진 프로필이 있다. 디스패처 한도는 열려 있다. */
    private AnalyzeUseCase useCaseWithGateVersion(String storedPolicyVersion) {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.IMPACT, Set.of(QueryType.IMPACT), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "영향");
        QueryPlanner planner = new QueryPlanner(q -> draft, new SourceAnalyzer(new FakeLookup()));
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law());
        AnalysisEngine engine = new AnalysisEngine(new ContextBuilder(), groundedReasoner(), 3);
        DimensionHandlerRegistry registry =
                new DimensionHandlerRegistry(List.of(new SummaryHandler(engine, law -> Optional.empty(), new ContextBuilder()), new DiffHandler(engine)));
        OffsetDateTime now = OffsetDateTime.parse("2026-10-28T00:00:00Z");
        StoredProfile stored = new StoredProfile(new UserProfile(null, 34, null, null, null, null, null),
                storedPolicyVersion, now, now);
        return new AnalyzeUseCase(planner, new QueryDispatcher(src, registry, new PersonalizedHandler(null), userId -> true, NO_DISCOVERY),
                (lawId, efYd) -> true, gate(stored, "v1"));
    }

    @Test
    void 로그인_사용자의_게이트_판정이_디스패처까지_간다_옛_동의면_개인화_는_CONSENT_OUTDATED() {
        AnalysisOutcome outcome = useCaseWithGateVersion("v0")
                .analyze("주택법 내 영향", null, Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")));

        AnalysisOutcome.Analyzed analyzed = assertInstanceOf(AnalysisOutcome.Analyzed.class, outcome);
        assertEquals(Unmet.Reason.CONSENT_OUTDATED, analyzed.result().unmet().get(QueryType.IMPACT).reason());
    }

    @Test
    void 해소되는_질의는_Analyzed로_SUMMARY가_채워진다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "요약");

        AnalysisOutcome out = useCase(draft, true).analyze("주택법 뭐가 바뀌어?", null, Optional.empty());

        AnalysisOutcome.Analyzed a = assertInstanceOf(AnalysisOutcome.Analyzed.class, out);
        assertTrue(a.result().filled().containsKey(QueryType.SUMMARY), "SUMMARY 차원이 채워져야");
        assertTrue(a.result().fullySatisfied(), "SUMMARY 단일 질의는 완전 충족");
    }

    @Test
    void explicitRef가_있으면_법명_해소를_건너뛰고_그_참조로_분석한다() {
        // targetKind=DISCOVERY + 법명 없음 → explicitRef 없으면 Discovery로 갔을 초안.
        // explicitRef를 주면 해소를 건너뛰고 그 참조로 Reference 분석해야 한다.
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.DISCOVERY,
                null, null, List.of(), List.of(), List.of(), "요약");
        LawRef pinned = new LawRef("001809", LocalDate.of(2026, 8, 4), null);

        AnalysisOutcome out = useCase(draft, true).analyze("법명 안 대는 자연어", pinned, Optional.empty());

        AnalysisOutcome.Analyzed a = assertInstanceOf(AnalysisOutcome.Analyzed.class, out);
        Target.Reference ref = assertInstanceOf(Target.Reference.class, a.query().target(),
                "explicitRef가 Discovery 초안을 Reference로 강제");
        assertEquals("001809", ref.lawRef().lawId());
        assertTrue(a.result().filled().containsKey(QueryType.SUMMARY));
    }

    @Test
    void 미해소_비법령_질의는_Unresolved로_분석되지_않는다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), false, TargetKind.REFERENCE,
                null, null, List.of(), List.of(), List.of(), "점심 메뉴");

        AnalysisOutcome out = useCase(draft, false).analyze("오늘 점심 뭐 먹지", null, Optional.empty());

        AnalysisOutcome.Unresolved u = assertInstanceOf(AnalysisOutcome.Unresolved.class, out);
        assertEquals(ResolutionState.UNVERIFIED, u.resolution().state());
    }

    @Test
    void 정본으로_답을_낸_Reference_분석은_조회로_센다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "요약");

        useCase(draft, true).analyze("주택법 뭐가 바뀌어?", null, Optional.empty());

        assertEquals(List.of("001809@2026-08-04"), recorded);
    }

    @Test
    void 발견_질의는_조회로_세지_않는다() {
        AnalysisQueryDraft discoveryDraft = new AnalysisQueryDraft(
                QueryType.LOOKUP, Set.of(QueryType.LOOKUP), true, TargetKind.DISCOVERY,
                null, null, List.of("전세"), List.of(), List.of(), "찾기");

        AnalysisOutcome out = useCase(discoveryDraft, true).analyze("전세 관련 바뀌는 법", null, Optional.empty(), false);

        AnalysisOutcome.Analyzed analyzed = assertInstanceOf(AnalysisOutcome.Analyzed.class, out);
        assertNotNull(analyzed.result().discovery());
        assertEquals(List.of(), recorded, "발견은 법령을 연 게 아니라 조회 집계 0회(D66)");
    }

    @Test
    void 정본이_없거나_미해소면_세지_않는다() {
        AnalysisQueryDraft reference = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "요약");
        AnalysisQueryDraft offTopic = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), false, TargetKind.REFERENCE,
                null, null, List.of(), List.of(), List.of(), "잡담");

        useCase(reference, false).analyze("주택법 뭐가 바뀌어?", null, Optional.empty());   // 정본 미적재 → 전부 unmet
        useCase(offTopic, true).analyze("오늘 점심 뭐 먹지", null, Optional.empty());

        assertEquals(List.of(), recorded);
    }

    @Test
    void 조회_기록이_실패해도_분석_결과는_그대로다() {
        AnalysisQueryDraft draft = new AnalysisQueryDraft(
                QueryType.SUMMARY, Set.of(QueryType.SUMMARY), true, TargetKind.REFERENCE,
                "주택법", null, List.of(), List.of(), List.of(), "요약");
        QueryPlanner planner = new QueryPlanner(q -> draft, new SourceAnalyzer(new FakeLookup()));
        FakeLawSource src = new FakeLawSource();
        src.versions.put("001809@2026-08-04", law());
        AnalysisEngine engine = new AnalysisEngine(new ContextBuilder(), groundedReasoner(), 3);
        AnalyzeUseCase failing = new AnalyzeUseCase(planner,
                new QueryDispatcher(src, new DimensionHandlerRegistry(List.of(new SummaryHandler(engine, law -> Optional.empty(), new ContextBuilder()))),
                        new PersonalizedHandler(null), userId -> false, NO_DISCOVERY),
                (lawId, efYd) -> { throw new IllegalStateException("DB 끊김"); }, anonymousGate());

        AnalysisOutcome out = failing.analyze("주택법 뭐가 바뀌어?", null, Optional.empty());

        AnalysisOutcome.Analyzed analyzed = assertInstanceOf(AnalysisOutcome.Analyzed.class, out);
        assertTrue(analyzed.result().filled().containsKey(QueryType.SUMMARY));
    }

    @Test
    void 발견_후보로_다시_묻는_질의는_LOOKUP_없이_요약이_채워진다() {
        // 재요청 = 원래 발견 질문 + lawRef — 번역기는 질문만 보고 LOOKUP 을 낸다(D70).
        AnalysisQueryDraft discoveryDraft = new AnalysisQueryDraft(
                QueryType.LOOKUP, Set.of(QueryType.LOOKUP), true, TargetKind.DISCOVERY,
                null, null, List.of("전세"), List.of(), List.of(), "찾기");
        LawRef pinned = new LawRef("001809", LocalDate.of(2026, 8, 4), null);

        AnalysisOutcome out = useCase(discoveryDraft, true).analyze("전세 관련 바뀌는 법", pinned, Optional.empty());

        AnalysisOutcome.Analyzed analyzed = assertInstanceOf(AnalysisOutcome.Analyzed.class, out);
        assertTrue(analyzed.result().filled().containsKey(QueryType.SUMMARY), "빈 S02 가 아니라 요약이 채워진다");
        assertFalse(analyzed.result().unmet().containsKey(QueryType.LOOKUP), "LOOKUP 은 Reference 에서 unmet 도 아니다");
    }
}
