package com.lia.core.testsupport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;

/** 저장 상태를 만들기 위한 최소 법령. revision 은 {@code "rev-" + mst}. */
public final class TestLaws {

    private TestLaws() {}

    public static Law law(String lawId, String mst, Law.Status status, LocalDate effectiveDate) {
        return law(lawId, mst, "테스트법", status, effectiveDate);
    }

    public static Law law(String lawId, String mst, String title, Law.Status status, LocalDate effectiveDate) {
        return new Law(lawId, mst, title, status, Law.AmendKind.일부개정, Law.LawType.법률, "부처",
                LocalDate.of(2026, 1, 1), "1", effectiveDate, "공포 후 시행", Law.EnforcementType.유예,
                "개정이유", "개정문", List.of(), List.of(), List.of(), null, "url", "rev-" + mst, Instant.now());
    }

    /** 조문이 있는 주택법 — 제18조(본문 지정, 개정)·제19조(무변경). 근거 패널·법령 상세·API 슬라이스가 함께 쓴다. */
    public static Law housing(Law.Status status, LocalDate effectiveDate, String article18Text) {
        List<Article> articles = List.of(
                new Article("18", "사업계획의 통합심의", article18Text, true, Article.ChangeType.개정, null, null, effectiveDate, true, null),
                new Article("19", "제19조", "① 그대로다.", false, Article.ChangeType.없음, null, null, effectiveDate, true, null));
        return housing(status, effectiveDate, articles);
    }

    public static Law housing(Law.Status status, LocalDate effectiveDate, List<Article> articles) {
        List<Addendum> addenda = List.of(new Addendum("제1조", "시행일", Addendum.kindOf("시행일"),
                "이 법은 공포 후 6개월이 경과한 날부터 시행한다.", "21323", effectiveDate));
        return new Law("001809", "283191", "주택법", status, Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", effectiveDate, "공포 후 6개월", Law.EnforcementType.유예,
                "심의 절차를 간소화한다.", "주택법 일부를 다음과 같이 개정한다.", addenda, articles, List.of(),
                null, "url", "rev", Instant.now());
    }
}
