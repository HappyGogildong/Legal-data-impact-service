package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
 * Layer A 핸들러 단위 — Summary/Diff가 <b>올바른 AnalyzeRequest(차원·정본·기준선)</b>를 구성해
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
        List<Article> arts = List.of(new Article("18", "제목", "제18조 통합심의.",
                true, ChangeType.개정, null, null, LocalDate.of(2026, 8, 4), true, null));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예, "이유", "개정문",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    @Test
    void SummaryHandler는_SUMMARY_차원으로_정본을_위임한다() {
        CapturingEngine eng = new CapturingEngine();
        DispatchContext c = ctx(null);

        new SummaryHandler(eng).handle(c);

        assertEquals(QueryType.SUMMARY, eng.captured.dimension());
        assertSame(c.law(), eng.captured.law());
    }

    @Test
    void DiffHandler는_DIFF_차원으로_기준선과_함께_위임한다() {
        CapturingEngine eng = new CapturingEngine();
        Law baseline = law();
        DispatchContext c = ctx(baseline);

        new DiffHandler(eng).handle(c);

        assertEquals(QueryType.DIFF, eng.captured.dimension());
        assertSame(baseline, eng.captured.baseline());
    }
}
