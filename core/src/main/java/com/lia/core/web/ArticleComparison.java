package com.lia.core.web;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;

/**
 * 조문 대비 규칙 — 근거 패널({@link CitedSourceResolver})과 법령 상세({@link LawDetailMapper})가 같은 규칙을 쓴다.
 * 현행 원문은 시행예정본의 개정·삭제·이동 조문에만 있다(신설·무변경은 현행이 없다). 이동은 옛 번호로 찾는다.
 */
final class ArticleComparison {

    private ArticleComparison() {}

    /** 현행 원문이 필요한 변경인가 — 개정·삭제·이동. */
    static boolean comparable(Article article) {
        Article.ChangeType type = article.changeType();
        return type == Article.ChangeType.개정 || type == Article.ChangeType.삭제 || type == Article.ChangeType.이동;
    }

    /** 기준선에서 찾은 현행 원문. 대비 대상이 아니거나 기준선·조문이 없으면 null. */
    static String previousText(Article article, Law baseline) {
        if (baseline == null || !comparable(article)) return null;
        String baseNo = article.changeType() == Article.ChangeType.이동 && article.movedFrom() != null
                ? article.movedFrom() : article.no();
        Article base = baseline.article(baseNo);
        return base == null ? null : base.text();
    }

    /** 조문 제목이 있고 라벨과 다르면 "(제목)". 일부 원천은 제목 자리에 "제N조"를 넣어 둔다. */
    static String titleSuffix(Article article) {
        String title = article.title();
        if (title == null || title.isBlank() || title.equals(article.label())) return "";
        return "(" + title + ")";
    }
}
