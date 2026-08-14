package com.lia.core.pipeline.diff;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;

/**
 * DiffBuilder 단위 테스트 — 신설·삭제 확정과 diffVsCurrent 채우기.
 * 정렬키는 조문번호(D42), 대상은 changed=true 조문뿐(비용 레버).
 */
class DiffBuilderTest {

    private final DiffBuilder diffBuilder = new DiffBuilder();

    // --- 픽스처 헬퍼 -------------------------------------------------------

    private static Article art(String no, String text, boolean changed, ChangeType type) {
        return new Article(no, "제" + no + "조", text, changed, type,
                null, null, LocalDate.of(2026, 8, 4), true, null);
    }

    private static Article moved(String no, String from, String text) {
        return new Article(no, "이동조문", text, true, ChangeType.이동,
                from, null, LocalDate.of(2026, 8, 4), true, null);
    }

    private static Law law(Law.Status status, LocalDate effective, List<Article> articles) {
        return new Law("001809", "MST-" + status, "주택법", status,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", effective,
                null, Law.EnforcementType.즉시, null, null,
                List.of(), articles, List.of(), null, null, "rev", Instant.now());
    }

    private static Law pending(List<Article> articles) {
        return law(Law.Status.시행예정, LocalDate.of(2026, 8, 4), articles);
    }

    private static Law baseline(List<Article> articles) {
        return law(Law.Status.시행중, LocalDate.of(2020, 1, 1), articles);
    }

    // --- 테스트 -----------------------------------------------------------

    @Nested
    @DisplayName("변경 조문 대조")
    class ChangedArticles {

        @Test
        @DisplayName("기준선에 없는 조문은 신설로 확정한다")
        void 신설을_확정한다() {
            Law p = pending(List.of(art("49", "제49조(현장점검) 요청할 수 있다.", true, ChangeType.없음)));
            Law b = baseline(List.of(art("18", "제18조 …", false, ChangeType.없음)));

            Article r = diffBuilder.build(p, b).article("49");

            assertEquals(ChangeType.신설, r.changeType());
            assertTrue(r.diffVsCurrent().contains("[신설]"), r.diffVsCurrent());
        }

        @Test
        @DisplayName("양쪽에 있고 자구가 다르면 개정 — 현행·개정 본문을 함께 담는다")
        void 개정을_대조한다() {
            Law p = pending(List.of(art("18", "제18조 통합하여 검토할 수 있다.", true, ChangeType.개정)));
            Law b = baseline(List.of(art("18", "제18조 개별로 검토한다.", false, ChangeType.없음)));

            Article r = diffBuilder.build(p, b).article("18");

            assertEquals(ChangeType.개정, r.changeType());
            assertTrue(r.diffVsCurrent().contains("현행: 제18조 개별로 검토한다."), r.diffVsCurrent());
            assertTrue(r.diffVsCurrent().contains("개정: 제18조 통합하여 검토할 수 있다."), r.diffVsCurrent());
        }

        @Test
        @DisplayName("\"삭제\" 마커 조문은 삭제로 확정한다 (개정일 주석 포함)")
        void 삭제를_확정한다() {
            Law p = pending(List.of(art("104", "제104조(벌칙) 삭제 <2026. 2. 3.>", true, ChangeType.개정)));
            Law b = baseline(List.of(art("104", "제104조(벌칙) 2년 이하의 징역에 처한다.", false, ChangeType.없음)));

            Article r = diffBuilder.build(p, b).article("104");

            assertEquals(ChangeType.삭제, r.changeType());
            assertTrue(r.diffVsCurrent().contains("→ 삭제"), r.diffVsCurrent());
            assertTrue(r.diffVsCurrent().contains("2년 이하의 징역"), r.diffVsCurrent());
        }

        @Test
        @DisplayName("이동 조문은 옛 번호로 기준선을 찾아 대조한다")
        void 이동을_대조한다() {
            Law p = pending(List.of(moved("57", "56", "제57조 이동된 내용 일부 개정.")));
            Law b = baseline(List.of(art("56", "제56조 원래 내용.", false, ChangeType.없음)));

            Article r = diffBuilder.build(p, b).article("57");

            assertEquals(ChangeType.이동, r.changeType());
            assertTrue(r.diffVsCurrent().contains("제56조 → 제57조"), r.diffVsCurrent());
            assertTrue(r.diffVsCurrent().contains("현행: 제56조 원래 내용."), r.diffVsCurrent());
        }

        @Test
        @DisplayName("플래그는 Y인데 자구가 동일하면 개정으로 두되 표기한다")
        void 자구가_동일하면_표기한다() {
            Law p = pending(List.of(art("28", "제28조  동일  본문.", true, ChangeType.개정)));
            Law b = baseline(List.of(art("28", "제28조 동일 본문.", false, ChangeType.없음)));

            Article r = diffBuilder.build(p, b).article("28");

            assertEquals(ChangeType.개정, r.changeType());
            assertTrue(r.diffVsCurrent().contains("자구 동일"), r.diffVsCurrent());
        }
    }

    @Nested
    @DisplayName("경계 조건")
    class Boundaries {

        @Test
        @DisplayName("기준선이 없으면(제정) 변경 조문은 모두 신설이다")
        void 제정은_전부_신설이다() {
            Law p = pending(List.of(
                    art("1", "제1조(목적) …", true, ChangeType.없음),
                    art("2", "제2조(정의) …", true, ChangeType.없음)));

            Law r = diffBuilder.build(p, null);

            assertEquals(ChangeType.신설, r.article("1").changeType());
            assertEquals(ChangeType.신설, r.article("2").changeType());
        }

        @Test
        @DisplayName("변경되지 않은 조문은 손대지 않는다 — diffVsCurrent 는 null")
        void 미변경_조문은_보존한다() {
            Article unchanged = art("2", "제2조(정의) …", false, ChangeType.없음);
            Law p = pending(List.of(unchanged, art("18", "제18조 개정.", true, ChangeType.개정)));
            Law b = baseline(List.of(art("2", "제2조(정의) …", false, ChangeType.없음),
                    art("18", "제18조 현행.", false, ChangeType.없음)));

            Law r = diffBuilder.build(p, b);

            assertNull(r.article("2").diffVsCurrent(), "미변경 조문은 대조하지 않는다");
            assertEquals(ChangeType.없음, r.article("2").changeType());
            assertNotNull(r.article("18").diffVsCurrent());
        }

        @Test
        @DisplayName("시행예정본은 필수 — null 이면 거부")
        void 시행예정본_없으면_거부한다() {
            assertThrows(IllegalArgumentException.class, () -> diffBuilder.build(null, baseline(List.of())));
        }

        @Test
        @DisplayName("기준선에 대응 조문이 없어도(획득 갭) 신설로 처리하고 깨지지 않는다")
        void 기준선_대응조문_부재를_견딘다() {
            Law p = pending(List.of(art("106", "제106조(과태료) …", true, ChangeType.개정)));
            Law b = baseline(List.of(art("18", "제18조 …", false, ChangeType.없음)));

            Article r = diffBuilder.build(p, b).article("106");

            assertEquals(ChangeType.신설, r.changeType());
        }
    }
}
