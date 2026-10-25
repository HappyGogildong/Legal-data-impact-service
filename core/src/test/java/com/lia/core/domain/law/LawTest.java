package com.lia.core.domain.law;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Article.ChangeType;

/**
 * 장·절 제목 행은 본문 첫 조문과 조문번호가 같다(실측: 같은 버전에서 번호가 겹치는 쌍 12,528건).
 * 조문 조회·변경 조문 선별은 실조문만 대상으로 해야 한다.
 */
class LawTest {

    private static Article heading(String no, String text, boolean changed) {
        return new Article(no, "총칙", text, changed, changed ? ChangeType.개정 : ChangeType.없음,
                null, null, null, false, null);
    }

    private static Article article(String no, String text, boolean changed) {
        return new Article(no, "정의", text, changed, changed ? ChangeType.개정 : ChangeType.없음,
                null, null, null, true, null);
    }

    private static Law lawOf(List<Article> articles) {
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                null, Law.EnforcementType.즉시, null, null,
                List.of(), articles, List.of(), null, null, "rev", Instant.now());
    }

    @Test
    void 조문_조회는_같은_번호의_제목_행이_먼저_와도_실조문을_돌려준다() {
        Law law = lawOf(List.of(heading("1", "제1장 총칙", false), article("1", "제1조(목적) …", false)));

        Article found = law.article("1");

        assertNotNull(found);
        assertTrue(found.isArticle());
        assertEquals("제1조(목적) …", found.text());
    }

    @Test
    void 조문_조회는_제목_행만_있는_번호에는_null이다() {
        Law law = lawOf(List.of(heading("9", "제2장 사업", false)));

        assertNull(law.article("9"));
    }

    @Test
    void 변경_조문은_변경된_제목_행을_제외한다() {
        Law law = lawOf(List.of(
                heading("18", "제3장 사업계획", true), article("18", "제18조 개정.", true),
                article("19", "제19조 그대로.", false)));

        List<Article> changed = law.changedArticles();

        assertEquals(1, changed.size());
        assertTrue(changed.get(0).isArticle());
        assertEquals("제18조 개정.", changed.get(0).text());
    }

    @Test
    void 변경된_제목_행만_있으면_변경_조문은_비어_있다() {
        Law law = lawOf(List.of(heading("18", "제3장 사업계획", true), article("18", "제18조 그대로.", false)));

        assertTrue(law.changedArticles().isEmpty());
    }
}
