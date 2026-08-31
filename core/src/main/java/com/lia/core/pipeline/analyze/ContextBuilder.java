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

        // 변경 조문 — 인용키 LAW:{lawId}@{efYd}:art:{no}
        for (Article a : law.changedArticles()) {
            blocks.add(new SourceBlock(law.sourceId(a), "article", a.text()));
        }
        // 개정문 — 자구 변경 근거. 인용키 …:amend
        if (law.amendText() != null && !law.amendText().isBlank()) {
            blocks.add(new SourceBlock(law.ref() + ":amend", "amend", law.amendText()));
        }
        // 부칙 — 시행일·경과조치·적용례. 인용키 …:addenda:{no}
        for (Addendum ad : law.addenda()) {
            blocks.add(new SourceBlock(law.ref() + ":addenda:" + ad.no(), "addenda", ad.text()));
        }
        // DIFF — 변경 조문의 시행중(baseline) 대응 조문. 인용키 LAW:{lawId}:art:{no}(시행일 없음)
        if (req.dimension() == QueryType.DIFF && req.baseline() != null) {
            Law baseline = req.baseline();
            for (Article a : law.changedArticles()) {
                Article base = baseline.article(a.no());
                if (base != null) {
                    blocks.add(new SourceBlock(baseline.sourceId(base), "baseline", base.text()));
                }
            }
        }

        return new AnalysisContext(req.dimension(), law.ref(), blocks);
    }
}
