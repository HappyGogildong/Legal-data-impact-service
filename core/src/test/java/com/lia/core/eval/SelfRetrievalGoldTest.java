package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.eval.GoldenSet.RetrievalCase;

/**
 * SelfRetrievalGold 단위 — 적재된 코퍼스에서 <b>자기검색 골든셋</b>을 자동 생성.
 * 각 법령: query=제개정이유(요약), expected = 그 법령의 source_ids(요약 ref + 변경조문). 순수 함수.
 */
class SelfRetrievalGoldTest {

    @Test
    void 각_법령마다_요약질의와_자기_source_ids를_만든다() {
        Law housing = law("주택법", "001809", LocalDate.of(2026, 8, 4), "심의 효율화 개정이유");

        List<RetrievalCase> gold = SelfRetrievalGold.fromLaws(List.of(housing));

        assertEquals(1, gold.size());
        RetrievalCase c = gold.get(0);
        assertEquals("심의 효율화 개정이유", c.query(), "query=제개정이유");
        assertTrue(c.expectedSourceIds().contains(housing.ref()), "요약 source_id(=ref) 포함");
        assertTrue(c.expectedSourceIds().contains(housing.sourceId(housing.article("18"))), "변경조문 포함");
        assertTrue(c.expectedSourceIds().contains(housing.sourceId(housing.article("104"))));
        // 미변경 조문은 색인 대상이 아니므로 기대에 없음
        assertEquals(3, c.expectedSourceIds().size(), "요약 1 + 변경조문 2");
    }

    @Test
    void amendReason이_없으면_케이스를_만들지_않는다() {
        Law noSummary = law("주택법", "001809", LocalDate.of(2026, 8, 4), null);
        assertTrue(SelfRetrievalGold.fromLaws(List.of(noSummary)).isEmpty(), "요약 없으면 자기검색 불가");
    }

    private static Law law(String title, String lawId, LocalDate efYd, String amendReason) {
        List<Article> arts = List.of(
                article("18", true, ChangeType.개정),
                article("104", true, ChangeType.개정),
                article("2", false, ChangeType.없음));   // 미변경 — 색인 안 됨
        return new Law(lawId, "283191", title, Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", efYd, "공포 후 6개월", Law.EnforcementType.유예,
                amendReason, "개정문", List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    private static Article article(String no, boolean changed, ChangeType type) {
        return new Article(no, "제목", "본문", changed, type, null, null, LocalDate.of(2026, 8, 4), true, null);
    }
}
