package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;

/** 질문이 조문을 지목한 변경점(D82) — 저장본을 그 조문으로 좁히고, 온라인 Opus 는 부르지 않는다. */
class DiffHandlerArticleTest {

    private static final String PENDING = "LAW:001809@2026-08-04";
    private static final String BASELINE = "LAW:001809";
    private static final ImpactResult.Claim ABOUT_18 = claim("제18조가 바뀐다", PENDING + ":art:18");
    private static final ImpactResult.Claim ABOUT_19 = claim("제19조가 바뀐다", BASELINE + ":art:19");
    private static final ImpactResult.Claim ABOUT_30 = claim("제25조가 제30조로 옮겨진다", PENDING + ":art:30");
    private static final ImpactResult.Claim ABOUT_AMENDMENT = claim("자구를 고친다", PENDING + ":amend");
    private static final ImpactResult STORED = stored(ABOUT_18, ABOUT_19, ABOUT_30, ABOUT_AMENDMENT);

    private static ImpactResult.Claim claim(String statement, String citation) {
        return new ImpactResult.Claim(statement, List.of(citation), 0.9);
    }

    private static ImpactResult stored(ImpactResult.Claim... claims) {
        return new ImpactResult(PENDING, "DIFF", "법 전체 요약", List.of(claims), List.of(), List.of(),
                null, List.of("불확실"), "참고용", null);
    }

    private static Article article(String no, boolean changed, ChangeType type, String movedFrom) {
        return new Article(no, "제목", "제" + no + "조 본문.", changed, type, movedFrom, null,
                LocalDate.of(2026, 8, 4), true, null);
    }

    private static Law version(Law.Status status, Law.AmendKind kind, List<Article> articles) {
        return new Law("001809", "283191", "주택법", status, kind, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), articles, List.of(), null, null, "rev1", Instant.now());
    }

    private static Law pending() {
        return version(Law.Status.시행예정, Law.AmendKind.일부개정, List.of(
                article("3", false, ChangeType.없음, null),
                article("18", true, ChangeType.개정, null),
                article("19", true, ChangeType.개정, null),
                article("30", true, ChangeType.이동, "25")));
    }

    private static Law baseline() {
        return version(Law.Status.시행중, Law.AmendKind.일부개정, List.of(
                article("3", false, ChangeType.없음, null),
                article("18", false, ChangeType.없음, null),
                article("19", false, ChangeType.없음, null),
                article("25", false, ChangeType.없음, null),
                article("40", false, ChangeType.없음, null)));
    }

    private static DispatchContext ctx(Law law, Law baseline, String articleNo) {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), articleNo);
        return new DispatchContext(law, baseline, new AnalysisQuery(QueryType.DIFF, Set.of(QueryType.DIFF),
                new Target.Reference(ref), "의도", ArticleScope.CHANGED_ONLY, false, null));
    }

    /** 부르면 전체 답을 돌려주고 호출을 기록한다. */
    private static final class RecordingEngine extends AnalysisEngine {
        int calls;
        RecordingEngine() { super(new ContextBuilder(), context -> null, 1); }
        @Override public AnalyzeResponse analyze(AnalyzeRequest request) {
            calls++;
            return new AnalyzeResponse(STORED, Set.of());
        }
    }

    private final RecordingEngine engine = new RecordingEngine();

    private DiffHandler handler(ImpactResult stored, boolean live) {
        return new DiffHandler(engine, (law, baseline) -> Optional.ofNullable(stored), new ContextBuilder(), live);
    }

    private Unmet.Reason outOfScope(ImpactResult stored, DispatchContext context) {
        return assertThrows(ArticleOutOfScopeException.class, () -> handler(stored, false).handle(context)).reason();
    }

    @Test
    void 바뀐_조문은_그_조문을_인용한_주장만_남기고_법_전체_요약을_뺀다() {
        AnalyzeResponse response = handler(STORED, false).handle(ctx(pending(), baseline(), "18"));

        assertEquals(List.of(ABOUT_18), response.result().claims());
        assertNull(response.result().summary(), "법 전체를 설명하는 문장은 조문 답에 맞지 않는다");
        assertEquals(List.of("불확실"), response.result().uncertainties(), "불확실성은 숨기지 않는다");
        assertEquals("참고용", response.result().disclaimer());
        assertFalse(response.injectedSourceIds().isEmpty(), "근거 집합은 그대로 둔다");
        assertEquals(0, engine.calls, "온라인 Opus 호출 없음(D80)");
    }

    @Test
    void 기준선_조문만_인용한_주장도_그_조문의_주장이다() {
        AnalyzeResponse response = handler(STORED, false).handle(ctx(pending(), baseline(), "19"));

        assertEquals(List.of(ABOUT_19), response.result().claims());
    }

    @Test
    void 옮겨진_조문은_옛_번호로_물어도_찾는다() {
        AnalyzeResponse response = handler(STORED, false).handle(ctx(pending(), baseline(), "25"));

        assertEquals(List.of(ABOUT_30), response.result().claims());
    }

    @Test
    void 조문_표기가_달라도_같은_조문으로_좁힌다() {
        AnalyzeResponse response = handler(STORED, false).handle(ctx(pending(), baseline(), "제18조제1항"));

        assertEquals(List.of(ABOUT_18), response.result().claims());
    }

    @Test
    void 안_바뀐_조문은_저장본이_없어도_그렇다고_답한다() {
        assertEquals(Unmet.Reason.ARTICLE_NOT_CHANGED, outOfScope(null, ctx(pending(), baseline(), "3")));
        assertEquals(0, engine.calls);
    }

    @Test
    void 정본과_기준선_어디에도_없는_조문은_없다고_답한다() {
        assertEquals(Unmet.Reason.ARTICLE_NOT_FOUND, outOfScope(null, ctx(pending(), baseline(), "999")));
        assertEquals(0, engine.calls);
    }

    @Test
    void 제정이면_기준선이_없어도_조문을_판정한다() {
        Law enacted = version(Law.Status.시행예정, Law.AmendKind.제정,
                List.of(article("18", true, ChangeType.신설, null)));

        assertEquals(List.of(ABOUT_18),
                handler(STORED, false).handle(ctx(enacted, null, "18")).result().claims());
        assertEquals(Unmet.Reason.ARTICLE_NOT_FOUND, outOfScope(STORED, ctx(enacted, null, "999")));
    }

    @Test
    void 기준선에만_있는_조문은_없다고_단정하지_않고_저장본으로_판단한다() {
        DispatchContext context = ctx(pending(), baseline(), "40");

        assertThrows(NotPrecomputedException.class, () -> handler(STORED, false).handle(context));
    }

    @Test
    void 바뀐_조문을_인용한_주장이_없으면_준비되지_않은_것이다() {
        DispatchContext context = ctx(pending(), baseline(), "18");

        assertThrows(NotPrecomputedException.class, () -> handler(stored(ABOUT_AMENDMENT), false).handle(context));
    }

    @Test
    void 바뀐_조문인데_저장본이_없으면_준비되지_않은_것이다() {
        DispatchContext context = ctx(pending(), baseline(), "18");

        assertThrows(NotPrecomputedException.class, () -> handler(null, false).handle(context));
        assertEquals(0, engine.calls);
    }

    @Test
    void 시행예정이_아니면_조문을_따지지_않고_준비되지_않은_것이다() {
        Law inForce = version(Law.Status.시행중, Law.AmendKind.일부개정, List.of());
        DispatchContext context = ctx(inForce, baseline(), "999");

        assertThrows(NotPrecomputedException.class, () -> handler(STORED, false).handle(context));
    }

    @Test
    void 조문을_지목하지_않으면_저장본_그대로_답한다() {
        AnalyzeResponse response = handler(STORED, false).handle(ctx(pending(), baseline(), null));

        assertSame(STORED, response.result());
    }

    @Test
    void 실시간을_켠_로컬에서도_답을_그_조문으로_좁힌다() {
        AnalyzeResponse response = handler(null, true).handle(ctx(pending(), baseline(), "18"));

        assertEquals(1, engine.calls);
        assertEquals(List.of(ABOUT_18), response.result().claims());
    }
}
