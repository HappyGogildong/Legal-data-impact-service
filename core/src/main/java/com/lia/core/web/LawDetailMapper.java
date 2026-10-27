package com.lia.core.web;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.web.LawDetailResponse.ChangedArticle;
import com.lia.core.web.LawDetailResponse.LawSummary;
import com.lia.core.web.LawDetailResponse.SourceText;
import com.lia.core.web.LawDetailResponse.SummaryClaim;

/**
 * {@link Law}(+기준선) → {@link LawDetailResponse} 표현 매핑(S05). 조문 대비는 시행예정본일 때만 — 시행된 버전은
 * 기준선이 곧 자기 자신이다. 규칙은 근거 패널과 공용({@link ArticleComparison}). 설계: docs/components/web/LawCatalogApi.md
 */
@Component
public class LawDetailMapper {

    /**
     * @param baseline 시행중 기준선, 없으면 null(제정·시행된 버전)
     * @param summary  유효한 쉬운 요약, 없으면 null — 시행예정본일 때만 싣는다
     */
    public LawDetailResponse toBody(Law law, Law baseline, ImpactResult summary) {
        boolean pending = law.pending();
        Law comparedTo = pending ? baseline : null;
        List<ChangedArticle> changed = law.changedArticles().stream()
                .map(article -> changedArticle(law, article, comparedTo))
                .toList();
        Addendum clause = law.effectiveClause();
        return new LawDetailResponse(
                law.lawId(), law.title(), name(law.lawType()), law.ministry(),
                law.promulgateDate(), law.promulgateNo(), law.effectiveDate(), name(law.status()),
                name(law.amendKind()), law.sourceUrl(), law.amendReason(), name(law.enforcementType()),
                law.effectiveRule(), clause == null ? null : addendum(law, clause), changed,
                law.addenda().stream().map(addendum -> addendum(law, addendum)).toList(),
                law.delegationClauses(),
                comparedTo == null ? null : comparedTo.effectiveDate(),
                pending && summary != null ? summary(summary) : null);
    }

    private static LawSummary summary(ImpactResult result) {
        List<SummaryClaim> claims = result.claims() == null ? List.of() : result.claims().stream()
                .map(claim -> new SummaryClaim(claim.statement(),
                        claim.citations() == null ? List.of() : claim.citations()))
                .toList();
        return new LawSummary(result.summary(), claims,
                result.uncertainties() == null ? List.of() : result.uncertainties());
    }

    private static ChangedArticle changedArticle(Law law, Article article, Law baseline) {
        return new ChangedArticle(law.sourceId(article), article.no(),
                article.label() + ArticleComparison.titleSuffix(article),
                name(article.changeType()), article.text(), ArticleComparison.previousText(article, baseline));
    }

    private static SourceText addendum(Law law, Addendum addendum) {
        String title = addendum.title() == null || addendum.title().isBlank() ? "" : "(" + addendum.title() + ")";
        return new SourceText(law.addendumSourceId(addendum), "부칙 " + addendum.no() + title, addendum.text());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
