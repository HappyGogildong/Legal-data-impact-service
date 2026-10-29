package com.lia.core.application.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lia.core.application.profile.PersonalizationGate;
import com.lia.core.domain.analysis.ConfirmedFact;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationAnswer;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisContext;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.analyze.PersonalizedOutput;
import com.lia.core.pipeline.analyze.Reasoner;
import com.lia.core.pipeline.dispatch.PersonalizationQuota;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;
import com.lia.core.profile.UserProfileStore;
import com.lia.core.store.LawSource;

/**
 * ActionRevisionUseCase 단위(D71) — 게이트 순서(개인화 → 세션 질문 → 답 검증 → 정본 → 기준선 → 한도 → 엔진)를
 * 실 엔진 + 가짜 추론기·저장소로 본다. 실 LLM·DB·HTTP 세션 없음.
 */
class ActionRevisionUseCaseTest {

    static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final LawRef REF = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
    static final String ART = "ART:001809@2026-08-04:18";

    /** 호출 수·마지막 컨텍스트를 기록하는 추론기 — 정상은 주입된 근거를 인용한 대응 1건, ungrounded 면 근거 없는 대응. */
    static final class FakeReasoner implements Reasoner {
        int calls;
        AnalysisContext lastContext;
        boolean ungrounded;

        @Override
        public ImpactResult reason(AnalysisContext context) {
            throw new UnsupportedOperationException("Layer B 는 reasonPersonalized 로만 온다");
        }

        @Override
        public PersonalizedOutput reasonPersonalized(AnalysisContext context) {
            calls++;
            lastContext = context;
            List<String> basis = ungrounded ? List.of() : List.of(context.injectedSourceIds().iterator().next());
            return new PersonalizedOutput(new ImpactResult("LAW:001809@2026-08-04", "ACTION", null, List.of(),
                    List.of(), List.of(new ImpactResult.Action("신고한다", "시행 전", basis)), null,
                    List.of(), "참고용", null), List.of(), List.of());
        }
    }

    static final class FakeQuota implements PersonalizationQuota {
        boolean allow = true;
        int consumed;
        @Override
        public boolean tryConsume(UUID userId) {
            if (!allow) return false;
            consumed++;
            return true;
        }
    }

    static final class FakeLawSource implements LawSource {
        final Map<String, Law> versions = new HashMap<>();
        Law baseline;
        @Override public Optional<Law> find(String lawId, LocalDate efYd) { return Optional.ofNullable(versions.get(lawId + "@" + efYd)); }
        @Override public Optional<Law> findBaseline(String lawId) { return Optional.ofNullable(baseline); }
    }

    /** 세션 대신 Map 에 질문을 두는 가짜 보관소. */
    static final class FakeQuestions implements SituationQuestions {
        final Map<LawRef, List<SituationQuestion>> byRef = new HashMap<>();
        @Override public Optional<List<SituationQuestion>> find(LawRef ref) { return Optional.ofNullable(byRef.get(ref)); }
        @Override public void save(LawRef ref, List<SituationQuestion> questions) { byRef.put(ref, questions); }
    }

    final FakeReasoner reasoner = new FakeReasoner();
    final FakeQuota quota = new FakeQuota();
    final FakeLawSource source = new FakeLawSource();
    final FakeQuestions questions = new FakeQuestions();

    static Law law(Law.AmendKind kind) {
        List<Article> arts = List.of(new Article("18", "제목", "제18조 통합심의.",
                true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                kind, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    /** 저장소가 항상 같은 프로필 행(없으면 null)을 돌려주는 게이트 — DB 없이. */
    private static PersonalizationGate gate(StoredProfile stored) {
        UserProfileStore store = new UserProfileStore(null) {
            @Override public Optional<StoredProfile> find(UUID userId) { return Optional.ofNullable(stored); }
        };
        return new PersonalizationGate(store, "v1");
    }

    private static StoredProfile storedWithPolicy(String policyVersion) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-28T00:00:00Z");
        return new StoredProfile(new UserProfile(null, 34, null, null, null, null, null), policyVersion, now, now);
    }

    /** 정본(개정본)·기준선이 적재돼 있고 동의가 최신인 기본 구성. */
    private ActionRevisionUseCase useCase() {
        return useCase(storedWithPolicy("v1"));
    }

    private ActionRevisionUseCase useCase(StoredProfile stored) {
        source.versions.put("001809@2026-08-04", law(Law.AmendKind.일부개정));
        source.baseline = law(Law.AmendKind.일부개정);
        AnalysisEngine engine = new AnalysisEngine(new ContextBuilder(), reasoner, 3);
        return new ActionRevisionUseCase(gate(stored), source, quota, engine);
    }

    private void saveQuestionQ1() {
        questions.save(REF, List.of(new SituationQuestion("q1", "질문 q1", ART,
                SituationQuestion.AnswerType.YES_NO, List.of())));
    }

    private static final List<SituationAnswer> ANSWER_Q1_YES = List.of(new SituationAnswer("q1", "예"));

    @Test
    void 익명이면_PROFILE_REQUIRED_로_한도와_엔진을_건드리지_않는다() {
        saveQuestionQ1();

        RevisionOutcome out = useCase().revise(Optional.empty(), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.PROFILE_REQUIRED, notRevised.unmet().reason());
        assertEquals(0, quota.consumed);
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 옛_동의면_CONSENT_OUTDATED() {
        saveQuestionQ1();

        RevisionOutcome out = useCase(storedWithPolicy("v0")).revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.CONSENT_OUTDATED, notRevised.unmet().reason());
        assertEquals(0, quota.consumed);
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 세션에_질문이_없으면_SITUATION_EXPIRED() {
        RevisionOutcome out = useCase().revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.SITUATION_EXPIRED, notRevised.unmet().reason());
        assertEquals(0, quota.consumed);
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 모르는_questionId_는_IllegalArgumentException_이고_한도를_쓰지_않는다() {
        saveQuestionQ1();
        ActionRevisionUseCase useCase = useCase();

        assertThrows(IllegalArgumentException.class, () -> useCase.revise(
                Optional.of(USER), REF, List.of(new SituationAnswer("q9", "예")), questions));

        assertEquals(0, quota.consumed);
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 정본이_없으면_LAW_NOT_LOADED() {
        saveQuestionQ1();
        ActionRevisionUseCase useCase = useCase();
        source.versions.clear();

        RevisionOutcome out = useCase.revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.LAW_NOT_LOADED, notRevised.unmet().reason());
        assertEquals(0, quota.consumed);
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 개정본인데_기준선이_없으면_BASELINE_NOT_LOADED() {
        saveQuestionQ1();
        ActionRevisionUseCase useCase = useCase();
        source.baseline = null;

        RevisionOutcome out = useCase.revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.BASELINE_NOT_LOADED, notRevised.unmet().reason());
        assertEquals(0, quota.consumed);
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 하루_한도를_넘으면_DAILY_LIMIT_이고_엔진을_부르지_않는다() {
        saveQuestionQ1();
        quota.allow = false;

        RevisionOutcome out = useCase().revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.DAILY_LIMIT, notRevised.unmet().reason());
        assertEquals(0, reasoner.calls);
    }

    @Test
    void 정상이면_한도_1회_차감하고_ACTION_으로_상황_사실을_넘긴다() {
        saveQuestionQ1();

        RevisionOutcome out = useCase().revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        assertInstanceOf(RevisionOutcome.Revised.class, out);
        assertEquals(1, quota.consumed);
        assertEquals(QueryType.ACTION, reasoner.lastContext.dimension());
        assertEquals(List.of(new ConfirmedFact("질문 q1", "예")), reasoner.lastContext.situation());
    }

    @Test
    void 엔진이_근거_부족이면_INSUFFICIENT_GROUNDING_이고_한도는_환불하지_않는다() {
        saveQuestionQ1();
        reasoner.ungrounded = true;

        RevisionOutcome out = useCase().revise(Optional.of(USER), REF, ANSWER_Q1_YES, questions);

        RevisionOutcome.NotRevised notRevised = assertInstanceOf(RevisionOutcome.NotRevised.class, out);
        assertEquals(Unmet.Reason.INSUFFICIENT_GROUNDING, notRevised.unmet().reason());
        assertEquals(1, quota.consumed);
    }
}
