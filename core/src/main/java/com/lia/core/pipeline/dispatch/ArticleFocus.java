package com.lia.core.pipeline.dispatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;

/**
 * 질문이 지목한 조문으로 변경점을 좁힌다(D82). 온라인 Opus 를 부르지 않으려고(D80) 조문별로 다시 만들지 않고
 * 법 전체 저장본에서 그 조문을 인용한 주장만 고른다. 주장의 인용은 저장·조회 때 근거 집합으로 검증돼 있어 믿을 수 있다.
 */
final class ArticleFocus {

    private ArticleFocus() {}

    /**
     * 지목한 조문이 변경 범위 안인지 데이터로 판정한다. 정본에 없어도 기준선에 있으면 없다고 단정하지 않는다 —
     * 시행예정본은 조문 전문을 주므로 데이터 이상일 수 있어 저장본 판단({@link #narrow})으로 넘긴다.
     *
     * @throws ArticleOutOfScopeException 없는 조문·이번에 안 바뀐 조문
     */
    static void requireChanged(Law law, Law baseline, String articleNo) {
        Article article = find(law, articleNo);
        if (article == null) {
            if (baseline == null || baseline.article(articleNo) == null) {
                throw new ArticleOutOfScopeException(Unmet.Reason.ARTICLE_NOT_FOUND,
                        Article.labelOf(articleNo) + " 없음: " + law.ref());
            }
            return;
        }
        if (!article.changed()) {
            throw new ArticleOutOfScopeException(Unmet.Reason.ARTICLE_NOT_CHANGED,
                    Article.labelOf(articleNo) + " 변경 없음: " + law.ref());
        }
    }

    /**
     * 그 조문을 인용한 주장만 남긴다. {@code summary} 는 법 전체 설명이라 빼고, 불확실성은 인용이 없어
     * 조문별로 가를 수 없으니 숨기지 않고 둔다.
     *
     * @throws NotPrecomputedException 그 조문을 인용한 주장이 없다
     */
    static ImpactResult narrow(ImpactResult result, Law law, Law baseline, String articleNo) {
        Set<String> sourceIds = sourceIds(law, baseline, articleNo);
        List<ImpactResult.Claim> claims = result.claims() == null ? List.of() : result.claims().stream()
                .filter(claim -> claim.citations() != null && claim.citations().stream().anyMatch(sourceIds::contains))
                .toList();
        if (claims.isEmpty()) {
            throw new NotPrecomputedException(Article.labelOf(articleNo) + " 설명 없음: " + law.ref());
        }
        return new ImpactResult(result.lawRef(), result.command(), null, claims, result.impacts(), result.actions(),
                result.effectiveInfo(), result.uncertainties(), result.disclaimer(), result.meta());
    }

    /** 번호로 찾되, 옮겨진 조문은 옛 번호로도 찾는다. */
    private static Article find(Law law, String articleNo) {
        Article article = law.article(articleNo);
        if (article != null) return article;
        return law.realArticles().stream()
                .filter(candidate -> articleNo.equals(candidate.movedFrom()))
                .findFirst().orElse(null);
    }

    /** 그 조문의 인용키 — 시행예정본과, {@code ContextBuilder} 가 같은 번호로 넣어 주는 기준선 조문. */
    private static Set<String> sourceIds(Law law, Law baseline, String articleNo) {
        Set<String> ids = new HashSet<>();
        Article article = find(law, articleNo);
        if (article != null) ids.add(law.sourceId(article));
        if (baseline != null) {
            Article base = baseline.article(article != null ? article.no() : articleNo);
            if (base != null) ids.add(baseline.sourceId(base));
        }
        return ids;
    }
}
