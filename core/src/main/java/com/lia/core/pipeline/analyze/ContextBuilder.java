package com.lia.core.pipeline.analyze;

import java.util.ArrayList;
import java.util.List;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisContext.SourceBlock;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 해소된 정본에서 <b>차원별 근거 블록 + source_id</b>를 조립한다(결정론, 검색 없음).
 *
 * <p>이 클래스가 부여한 {@code source_id}가 곧 인용 게이트의 기준이다 — 그라운딩 임계 로직이라 독립 검증한다.
 * 스펙: docs/components/AnalysisEngine.md · docs/prompts/analysis-prompt-spec.md §2·§3.
 */
public class ContextBuilder {

    public AnalysisContext build(AnalyzeRequest req) {
        Law law = req.law();
        List<SourceBlock> blocks = new ArrayList<>();

        // 변경 조문 — source_id 포맷은 Law이 권위(문자열 조합 금지)
        for (Article a : law.changedArticles()) {
            blocks.add(new SourceBlock(law.sourceId(a), SourceType.ARTICLE, a.text()));
        }
        // 개정문 — 자구 변경 근거
        if (law.amendText() != null && !law.amendText().isBlank()) {
            blocks.add(new SourceBlock(law.amendSourceId(), SourceType.AMEND, law.amendText()));
        }
        // 부칙 — 시행일·경과조치·적용례
        for (Addendum ad : law.addenda()) {
            blocks.add(new SourceBlock(law.addendumSourceId(ad), SourceType.ADDENDA, ad.text()));
        }
        // 기준선 — DIFF(대조)와 Layer B(바뀐 점을 알아야 영향을 판단, D69). baseline=null이면 제정(전부 신설, D42) → 스킵
        boolean needsBaseline = req.dimension() == QueryType.DIFF || req.dimension().isPersonalized();
        if (needsBaseline && req.baseline() != null) {
            Law baseline = req.baseline();
            for (Article a : law.changedArticles()) {
                Article base = baseline.article(a.no());
                if (base != null) {
                    blocks.add(new SourceBlock(baseline.sourceId(base), SourceType.BASELINE, base.text()));
                }
            }
        }

        return new AnalysisContext(req.dimension(), law.ref(), blocks, req.profile(), req.question(), req.situation());
    }
}
