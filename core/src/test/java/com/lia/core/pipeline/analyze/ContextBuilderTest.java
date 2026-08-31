package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisContext.SourceBlock;
import com.lia.core.pipeline.plan.QueryType;

/**
 * ContextBuilder 단위 — 해소된 정본에서 <b>차원별 근거 블록 + source_id</b>를 조립한다.
 * 순수·결정론(LLM·DB 무관). source_id = 인용 게이트의 기준이라 그라운딩 임계.
 */
class ContextBuilderTest {

    private final ContextBuilder builder = new ContextBuilder();

    @Test
    void summary는_변경조문을_source_id_블록으로_담는다() {
        Law law = housing();
        AnalysisContext ctx = builder.build(new AnalyzeRequest(QueryType.SUMMARY, law, null));

        Set<String> ids = ctx.injectedSourceIds();
        assertTrue(ids.contains(law.sourceId(law.article("18"))), "변경조문 제18조 주입 안 됨");
        assertTrue(ids.contains(law.sourceId(law.article("104"))), "변경조문 제104조 주입 안 됨");
        assertFalse(ids.contains(law.sourceId(law.article("2"))), "미변경 조문은 안 들어감");

        SourceBlock art18 = ctx.blocks().stream()
                .filter(b -> b.sourceId().equals(law.sourceId(law.article("18")))).findFirst().orElseThrow();
        assertEquals("article", art18.kind());
        assertTrue(art18.text().contains("통합심의"), "조문 본문이 블록에 담겨야");
    }

    @Test
    void 개정문과_부칙도_source_id_블록으로_담는다() {
        Law law = housing();
        AnalysisContext ctx = builder.build(new AnalyzeRequest(QueryType.SUMMARY, law, null));

        Set<String> ids = ctx.injectedSourceIds();
        assertTrue(ids.contains(law.ref() + ":amend"), "개정문 source_id 없음");
        assertTrue(ids.contains(law.ref() + ":addenda:제1조"), "부칙 source_id 없음");

        SourceBlock amend = ctx.blocks().stream()
                .filter(b -> b.sourceId().equals(law.ref() + ":amend")).findFirst().orElseThrow();
        assertEquals("amend", amend.kind());
        SourceBlock addenda = ctx.blocks().stream()
                .filter(b -> b.kind().equals("addenda")).findFirst().orElseThrow();
        assertTrue(addenda.text().contains("공포 후 6개월"), "부칙 본문");
    }

    @Test
    void diff는_baseline_대응조문도_담는다() {
        Law pending = housing();
        Law baseline = baseline();
        AnalysisContext ctx = builder.build(new AnalyzeRequest(QueryType.DIFF, pending, baseline));

        Set<String> ids = ctx.injectedSourceIds();
        // 시행중 조문 인용키는 시행일 없음: LAW:{lawId}:art:{no}
        assertTrue(ids.contains("LAW:001809:art:18"), "baseline 대응조문(제18조) 없음");
        assertTrue(ids.contains(pending.sourceId(pending.article("18"))), "시행예정 변경조문도 함께");

        SourceBlock base18 = ctx.blocks().stream()
                .filter(b -> b.sourceId().equals("LAW:001809:art:18")).findFirst().orElseThrow();
        assertEquals("baseline", base18.kind());
        assertTrue(base18.text().contains("개별로 심의"), "옛 조문 본문");
    }

    private static Law baseline() {
        List<Article> arts = List.of(
                article("18", "제18조(사업계획의 통합심의 등) 개별로 심의한다.", false, ChangeType.없음),
                article("104", "제104조(벌칙) 1년 이하의 징역에 처한다.", false, ChangeType.없음));
        return new Law("001809", "200000", "주택법", Law.Status.시행중,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2019, 12, 31), "16000", LocalDate.of(2020, 1, 1),
                null, null, null, null, List.of(), arts, List.of(), null, null, "base", Instant.now());
    }

    private static Law housing() {
        List<Article> arts = List.of(
                article("18", "제18조(사업계획의 통합심의 등) 통합하여 검토한다.", true, ChangeType.개정),
                article("104", "제104조(벌칙) 2년 이하의 징역에 처한다.", true, ChangeType.개정),
                article("2", "제2조 미변경 정의", false, ChangeType.없음));
        List<Addendum> addenda = List.of(
                new Addendum("제1조", "시행일", Addendum.Kind.시행일, "공포 후 6개월", "21323",
                        LocalDate.of(2026, 2, 3)));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예,
                "[일부개정] 심의 효율화", "주택법 일부를 개정한다. 제18조…",
                addenda, arts, List.of(), null, null, "rev1", Instant.now());
    }

    private static Article article(String no, String text, boolean changed, ChangeType type) {
        return new Article(no, "제목", text, changed, type, null, null, LocalDate.of(2026, 8, 4), true, null);
    }
}
