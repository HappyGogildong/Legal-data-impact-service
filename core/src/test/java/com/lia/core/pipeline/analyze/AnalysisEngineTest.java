package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;

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
