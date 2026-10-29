package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ConfirmedFact;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.UserProfile;

/**
 * AnalysisEngine 단위 — <b>오케스트레이션</b>(조립→추론→인용검증→재생성≤N→폴백)을 FakeReasoner로 검증.
 * 실 Opus 없음(비용·비결정 배제). 재생성 루프가 이 컴포넌트의 핵심 로직.
 */
class AnalysisEngineTest {

    /** 스크립트된 결과를 순서대로 뱉는 Fake — "첫 응답 환각→둘째 정상" 재생성 시나리오용. */
    static Reasoner scripted(ImpactResult... results) {
        Deque<ImpactResult> q = new ArrayDeque<>(List.of(results));
        return ctx -> q.isEmpty() ? results[results.length - 1] : q.poll();
    }

    private AnalysisEngine engine(Reasoner reasoner) {
        return new AnalysisEngine(new ContextBuilder(), reasoner, 3);
    }

    @Test
    void 인용_유효하면_그대로_반환하고_injectedSourceIds를_함께_준다() {
        Law law = housing();
        String cite = law.sourceId(law.article("18"));
        AnalysisEngine engine = engine(scripted(result(claim("제18조 심의가 통합된다", cite))));

        AnalyzeResponse resp = engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null));

        assertEquals("제18조 심의가 통합된다", resp.result().claims().get(0).statement());
        assertTrue(resp.injectedSourceIds().contains(cite), "게이트 입력용 injectedSourceIds 반환");
    }

    @Test
    void 환각_인용은_재생성으로_교정된다() {
        Law law = housing();
        String good = law.sourceId(law.article("18"));      // context에 있음
        String hallucinated = "LAW:999@2099-01-01:art:1";   // 주입 안 된 인용

        AnalysisEngine engine = engine(scripted(
                result(claim("환각 주장", hallucinated)),      // 1차 — 게이트 실패
                result(claim("정상 주장", good))));            // 2차 — 통과

        AnalyzeResponse resp = engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null));

        assertEquals("정상 주장", resp.result().claims().get(0).statement(), "재생성된 정상 결과가 나와야");
    }

    @Test
    void N회_모두_환각이면_근거부족_폴백() {
        Law law = housing();
        String hallucinated = "LAW:999@2099-01-01:art:1";
        AnalysisEngine engine = engine(scripted(result(claim("계속 환각", hallucinated))));

        assertThrows(InsufficientGroundingException.class,
                () -> engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null)));
    }

    @Test
    void 무인용_주장도_재생성_대상() {
        Law law = housing();
        AnalysisEngine engine = engine(scripted(result(claim("근거 없는 주장"))));   // citations 비어 있음
        assertThrows(InsufficientGroundingException.class,
                () -> engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null)));
    }

    @Test
    void impacts의_환각_인용도_거부되고_재생성된다() {
        Law law = housing();
        String good = law.sourceId(law.article("18"));      // 주입됨
        String hallucinated = "LAW:999@2099-01-01:art:1";   // 주입 안 됨

        AnalysisEngine engine = engine(scripted(
                resultWith(List.of(claim("정상 주장", good)),
                        List.of(impact("주거", hallucinated))),   // claims 정상이나 impacts 환각 → 게이트 실패
                resultWith(List.of(claim("정상 주장", good)), List.of())));  // 재생성 통과

        AnalyzeResponse resp = engine.analyze(new AnalyzeRequest(QueryType.DIFF, law, null));

        assertTrue(resp.result().impacts().isEmpty(),
                "impacts에 주입 안 된 source_id가 있으면 거부되고 재생성돼야");
    }

    @Test
    void claims가_null이면_NPE없이_근거부족_폴백() {
        Law law = housing();
        ImpactResult nullClaims = new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "요약",
                null, List.of(), List.of(), null, List.of(), "참고", null);   // claims 생략
        AnalysisEngine engine = engine(scripted(nullClaims));

        assertThrows(InsufficientGroundingException.class,
                () -> engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null)));
    }

    @Test
    void claims가_비어도_근거부족_폴백() {
        Law law = housing();
        AnalysisEngine engine = engine(scripted(result()));   // claims 빈 배열 = 근거 없음
        assertThrows(InsufficientGroundingException.class,
                () -> engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null)));
    }

    private static final String ART = "LAW:A@2027-01-01:art:1";

    private static ImpactResult layerB(List<ImpactResult.Impact> impacts, List<ImpactResult.Action> actions) {
        return new ImpactResult("LAW:A@2027-01-01", "IMPACT", "요약",
                List.of(new ImpactResult.Claim("주장", List.of(ART), 0.9)),
                impacts, actions, null, List.of(), "참고용", null);
    }

    @Test
    void LayerB_는_영향과_대응이_모두_있고_항목마다_인용이_있어야_통과() {
        Set<String> injected = Set.of(ART);
        ImpactResult ok = layerB(
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of(ART))),
                List.of(new ImpactResult.Action("확인", null, List.of(ART))));
        ImpactResult noActions = layerB(
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of(ART))), List.of());
        ImpactResult uncitedImpact = layerB(
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of())),
                List.of(new ImpactResult.Action("확인", null, List.of(ART))));
        ImpactResult basislessAction = layerB(
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of(ART))),
                List.of(new ImpactResult.Action("확인", null, List.of())));

        assertTrue(AnalysisEngine.grounded(QueryType.IMPACT, ok, injected));
        assertFalse(AnalysisEngine.grounded(QueryType.IMPACT, noActions, injected), "대응 없음");
        assertFalse(AnalysisEngine.grounded(QueryType.IMPACT, uncitedImpact, injected), "무인용 영향");
        assertFalse(AnalysisEngine.grounded(QueryType.IMPACT, basislessAction, injected), "무근거 대응");
    }

    @Test
    void 해당_없음도_조문_인용이_있으면_정상_답() {
        ImpactResult none = layerB(
                List.of(new ImpactResult.Impact("주거", "영향 없음", "적용 대상이 아님", List.of(ART))),
                List.of(new ImpactResult.Action("따로 할 일 없음", null, List.of(ART))));
        assertTrue(AnalysisEngine.grounded(QueryType.IMPACT, none, Set.of(ART)));
    }

    @Test
    void LayerA_는_영향_대응이_없어도_기존_규칙대로_통과() {
        ImpactResult summary = new ImpactResult("LAW:A@2027-01-01", "SUMMARY", "요약",
                List.of(new ImpactResult.Claim("주장", List.of(ART), 0.9)),
                List.of(), List.of(), null, List.of(), "참고용", null);
        assertTrue(AnalysisEngine.grounded(QueryType.SUMMARY, summary, Set.of(ART)));
    }

    private static final String HOUSING_ART = "LAW:001809@2026-08-04:art:18";
    private static final UserProfile PROFILE = new UserProfile(null, 34, null, null, null, null, null);

    /** housing 근거만 인용하는 개인화 결과 — 영향·대응 모두 인용 있음. */
    private static ImpactResult housingPersonalized() {
        return new ImpactResult("LAW:001809@2026-08-04", "IMPACT", "요약",
                List.of(new ImpactResult.Claim("주장", List.of(HOUSING_ART), 0.9)),
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of(HOUSING_ART))),
                List.of(new ImpactResult.Action("확인", null, List.of(HOUSING_ART))),
                null, List.of(), "참고용", null);
    }

    /** 개인화 가짜 추론 — reasonPersonalized 를 스크립트한다. */
    static Reasoner personalized(PersonalizedOutput out) {
        return new Reasoner() {
            public ImpactResult reason(AnalysisContext ctx) { throw new AssertionError("개인화는 reasonPersonalized"); }
            public PersonalizedOutput reasonPersonalized(AnalysisContext ctx) { return out; }
        };
    }

    @Test
    void 개인화_는_근거_안의_형식_맞는_질문만_최대_3개_q1부터_다시_번호() {
        List<SituationQuestion> raw = List.of(
                new SituationQuestion("f", "  ", HOUSING_ART, SituationQuestion.AnswerType.YES_NO, List.of()),
                q("a", HOUSING_ART), q("b", "LAW:없는:art:1"), q("c", HOUSING_ART), q("d", HOUSING_ART), q("e", HOUSING_ART));
        AnalysisEngine engine = engine(personalized(new PersonalizedOutput(housingPersonalized(), raw, List.of())));

        AnalyzeResponse r = engine.analyze(new AnalyzeRequest(QueryType.IMPACT, housing(), housing(), PROFILE, "내 영향"));

        assertEquals(List.of("q1", "q2", "q3"), r.situation().questions().stream().map(SituationQuestion::id).toList());
        assertEquals(List.of("질문 a", "질문 c", "질문 d"), r.situation().questions().stream().map(SituationQuestion::text).toList(),
                "빈 문장·근거 밖 질문은 버리고 순서 유지, 최대 3");
    }

    static SituationQuestion q(String id, String basis) {
        return new SituationQuestion(id, "질문 " + id, basis, SituationQuestion.AnswerType.YES_NO, List.of());
    }

    @Test
    void 단계_설명은_대응_범위_안_비지_않은_것만() {
        assertEquals(List.of(new StepNote(0, "이유")),
                AnalysisEngine.admissibleStepNotes(List.of(
                        new StepNote(0, "이유"), new StepNote(0, "중복"), new StepNote(5, "범위 밖"),
                        new StepNote(1, " "), new StepNote(1, "가".repeat(201))), 2));
    }

    @Test
    void ACTION_재생성은_claims_없이도_대응과_근거가_있으면_통과() {
        ImpactResult actionOnly = new ImpactResult("LAW:x", "ACTION", null, List.of(), List.of(),
                List.of(new ImpactResult.Action("갱신 요구", "2027-01", List.of(HOUSING_ART))), null, List.of(), "참고", null);
        AnalysisEngine engine = engine(personalized(
                new PersonalizedOutput(actionOnly, List.of(q("x", HOUSING_ART)), List.of(new StepNote(0, "시행일 이전 계약")))));

        AnalyzeResponse r = engine.analyze(new AnalyzeRequest(QueryType.ACTION, housing(), housing(), PROFILE,
                null, List.of(new ConfirmedFact("계약을 이전에 맺었나요?", "예"))));

        assertEquals(1, r.result().actions().size());
        assertEquals(List.of(new StepNote(0, "시행일 이전 계약")), r.situation().stepNotes());
        assertTrue(r.situation().questions().isEmpty(), "재생성은 새 질문을 내지 않는다");
    }

    @Test
    void ACTION_재생성에서_근거_없는_대응이면_재시도_후_근거_부족() {
        ImpactResult noBasis = new ImpactResult("LAW:x", "ACTION", null, List.of(), List.of(),
                List.of(new ImpactResult.Action("갱신 요구", null, List.of())), null, List.of(), "참고", null);
        int[] calls = {0};
        Reasoner counting = new Reasoner() {
            public ImpactResult reason(AnalysisContext ctx) { throw new AssertionError(); }
            public PersonalizedOutput reasonPersonalized(AnalysisContext ctx) {
                calls[0]++;
                return new PersonalizedOutput(noBasis, List.of(), List.of());
            }
        };
        AnalysisEngine engine = engine(counting);

        assertThrows(InsufficientGroundingException.class, () -> engine.analyze(new AnalyzeRequest(QueryType.ACTION,
                housing(), housing(), PROFILE, null, List.of(new ConfirmedFact("q", "예")))));
        assertEquals(3, calls[0]);
    }

    @Test
    void 상황_사실은_ACTION_요청에만() {
        assertThrows(IllegalArgumentException.class, () -> new AnalyzeRequest(QueryType.IMPACT,
                housing(), null, PROFILE, "질문", List.of(new ConfirmedFact("q", "예"))));
    }

    // --- fixtures --------------------------------------------------------

    static ImpactResult resultWith(List<ImpactResult.Claim> claims, List<ImpactResult.Impact> impacts) {
        return new ImpactResult("LAW:001809@2026-08-04", "DIFF", "요약",
                claims, impacts, List.of(), null, List.of(), "참고", null);
    }

    static ImpactResult.Impact impact(String aspect, String... citations) {
        return new ImpactResult.Impact(aspect, "영향 있음", "상세", List.of(citations));
    }

    static ImpactResult result(ImpactResult.Claim... claims) {
        return new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "요약",
                List.of(claims), List.of(), List.of(), null, List.of(), "참고용", null);
    }

    static ImpactResult.Claim claim(String statement, String... citations) {
        return new ImpactResult.Claim(statement, List.of(citations), 0.8);
    }

    static Law housing() {
        List<Article> arts = List.of(
                article("18", "제18조(사업계획의 통합심의 등) 통합하여 검토한다."),
                article("104", "제104조(벌칙) 2년 이하의 징역."));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    private static Article article(String no, String text) {
        return new Article(no, "제목", text, true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null);
    }
}
