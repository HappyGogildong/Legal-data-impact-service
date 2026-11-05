package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.*;

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

/**
 * Layer A 핸들러 단위 — 온라인은 저장본만 읽고(D80), 실시간을 켠 로컬에서만 <b>올바른 AnalyzeRequest(차원·정본·기준선)</b>를 구성해
 * AnalysisEngine에 위임하는지 확인. CapturingEngine으로 전달 인자를 포착한다.
 */
class LayerAHandlerTest {

    /** analyze를 가로채 전달된 AnalyzeRequest를 포착하는 테스트 엔진. */
    static final class CapturingEngine extends AnalysisEngine {
        AnalyzeRequest captured;
        CapturingEngine() { super(new ContextBuilder(), ctx -> null, 1); }
        @Override public AnalyzeResponse analyze(AnalyzeRequest req) {
            this.captured = req;
            return new AnalyzeResponse(null, Set.of());
        }
    }

    private static DispatchContext ctx(Law baseline) {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
        AnalysisQuery q = new AnalysisQuery(QueryType.SUMMARY, Set.of(QueryType.SUMMARY),
                new Target.Reference(ref), "의도", ArticleScope.CHANGED_ONLY, false, null);
        return new DispatchContext(law(), baseline, q);
    }

    static Law law() {
        return law(Law.Status.시행예정);
    }

    static Law law(Law.Status status) {
        List<Article> arts = List.of(new Article("18", "제목", "제18조 통합심의.",
                true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null));
        return new Law("001809", "283191", "주택법", status,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    private static final ImpactResult STORED = new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "저장본",
            List.of(new ImpactResult.Claim("바뀐다", List.of("LAW:001809@2026-08-04:art:18"), 0.9)),
            List.of(), List.of(), null, List.of(), "참고용", null);

    private static SummaryHandler summary(CapturingEngine engine, ImpactResult stored, boolean live) {
        return new SummaryHandler(engine, law -> Optional.ofNullable(stored), new ContextBuilder(), live);
    }

    private static DiffHandler diff(CapturingEngine engine, ImpactResult stored, boolean live) {
        return new DiffHandler(engine, (law, baseline) -> Optional.ofNullable(stored), new ContextBuilder(), live);
    }

    private static DispatchContext inForce() {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
        return new DispatchContext(law(Law.Status.시행중), law(Law.Status.시행중), new AnalysisQuery(QueryType.SUMMARY,
                Set.of(QueryType.SUMMARY), new Target.Reference(ref), "의도", ArticleScope.CHANGED_ONLY, false, null));
    }

    @Test
    void SummaryHandler는_유효한_저장본이_있으면_엔진을_부르지_않고_그것으로_답한다() {
        CapturingEngine eng = new CapturingEngine();

        AnalyzeResponse response = summary(eng, STORED, false).handle(ctx(null));

        assertNull(eng.captured, "저장본 적중 — 추론하지 않는다");
        assertSame(STORED, response.result());
        assertTrue(response.injectedSourceIds().contains("LAW:001809@2026-08-04:art:18"),
                "근거 집합은 정본으로 다시 조립한다(인용 칩 근거 해석용)");
    }

    @Test
    void SummaryHandler는_저장본이_없으면_엔진을_부르지_않고_미충족으로_알린다() {
        CapturingEngine eng = new CapturingEngine();

        assertThrows(NotPrecomputedException.class, () -> summary(eng, null, false).handle(ctx(null)));

        assertNull(eng.captured, "온라인 Opus 호출 없음(D80)");
    }

    @Test
    void SummaryHandler는_시행예정이_아니면_저장본을_쓰지_않고_미충족으로_알린다() {
        CapturingEngine eng = new CapturingEngine();

        assertThrows(NotPrecomputedException.class, () -> summary(eng, STORED, false).handle(inForce()));

        assertNull(eng.captured);
    }

    @Test
    void SummaryHandler는_실시간을_켜면_저장본이_없을_때_엔진에_위임한다() {
        CapturingEngine eng = new CapturingEngine();
        DispatchContext c = ctx(null);

        summary(eng, null, true).handle(c);

        assertEquals(QueryType.SUMMARY, eng.captured.dimension());
        assertSame(c.law(), eng.captured.law());
    }

    @Test
    void DiffHandler는_유효한_저장본이_있으면_엔진을_부르지_않고_기준선까지_근거로_답한다() {
        CapturingEngine eng = new CapturingEngine();
        Law baseline = law(Law.Status.시행중);

        AnalyzeResponse response = diff(eng, STORED, false).handle(ctx(baseline));

        assertNull(eng.captured, "저장본 적중 — 추론하지 않는다");
        assertSame(STORED, response.result());
        assertTrue(response.injectedSourceIds().contains(baseline.sourceId(baseline.article("18"))),
                "변경점 근거에는 기준선 조문도 들어간다");
    }

    @Test
    void DiffHandler는_저장본이_없으면_엔진을_부르지_않고_미충족으로_알린다() {
        CapturingEngine eng = new CapturingEngine();

        assertThrows(NotPrecomputedException.class, () -> diff(eng, null, false).handle(ctx(law())));

        assertNull(eng.captured, "온라인 Opus 호출 없음(D80)");
    }

    @Test
    void DiffHandler는_시행예정이_아니면_저장본을_쓰지_않고_미충족으로_알린다() {
        CapturingEngine eng = new CapturingEngine();

        assertThrows(NotPrecomputedException.class, () -> diff(eng, STORED, false).handle(inForce()));

        assertNull(eng.captured);
    }

    @Test
    void DiffHandler는_실시간을_켜면_저장본이_없을_때_기준선과_함께_위임한다() {
        CapturingEngine eng = new CapturingEngine();
        Law baseline = law();

        diff(eng, null, true).handle(ctx(baseline));

        assertEquals(QueryType.DIFF, eng.captured.dimension());
        assertSame(baseline, eng.captured.baseline());
    }

    @Test
    void DiffHandler는_baseline을_요구하고_SummaryHandler는_아니다() {
        CapturingEngine eng = new CapturingEngine();
        assertTrue(diff(eng, null, false).needsBaseline(), "DIFF는 대조 대상 baseline 필요");
        assertFalse(summary(eng, null, false).needsBaseline(), "SUMMARY는 baseline 불필요");
    }
}
