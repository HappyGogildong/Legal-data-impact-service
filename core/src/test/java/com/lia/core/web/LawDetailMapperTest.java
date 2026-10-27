package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.web.LawDetailResponse.ChangedArticle;
import com.lia.core.web.LawDetailResponse.SourceText;

/** 법령 상세 표현 — 정본 사실 그대로, 조문 대비는 근거 패널과 같은 규칙. */
class LawDetailMapperTest {

    static final LocalDate EF = LocalDate.of(2026, 11, 1);
    static final LocalDate BASELINE_EF = LocalDate.of(2020, 1, 1);

    final LawDetailMapper mapper = new LawDetailMapper();

    @Test
    @DisplayName("시행예정본 — 헤더·개정 이유·시행 규칙·바뀐 조문(현행 대비)·부칙·기준선 시행일")
    void 시행예정본() {
        Law law = CitedSourceResolverTest.law(Law.Status.시행예정, EF, "① 통합심의한다.");
        Law baseline = CitedSourceResolverTest.law(Law.Status.시행중, BASELINE_EF, "① 심의한다.");

        LawDetailResponse body = mapper.toBody(law, baseline, null);

        assertEquals("001809", body.lawId());
        assertEquals("주택법", body.title());
        assertEquals("법률", body.lawType());
        assertEquals("국토교통부", body.ministry());
        assertEquals(LocalDate.of(2026, 2, 3), body.promulgateDate());
        assertEquals("21323", body.promulgateNo());
        assertEquals(EF, body.effectiveDate());
        assertEquals("시행예정", body.status());
        assertEquals("일부개정", body.amendKind());
        assertEquals("url", body.sourceUrl());
        assertEquals("심의 절차를 간소화한다.", body.amendReason());
        assertEquals("유예", body.enforcementType());
        assertEquals("공포 후 6개월", body.effectiveRule());
        SourceText clause = new SourceText("LAW:001809@2026-11-01:addenda:제1조", "부칙 제1조(시행일)",
                "이 법은 공포 후 6개월이 경과한 날부터 시행한다.");
        assertEquals(clause, body.effectiveClause());
        assertEquals(List.of(new ChangedArticle("LAW:001809@2026-11-01:art:18", "18", "제18조(사업계획의 통합심의)",
                "개정", "① 통합심의한다.", "① 심의한다.")), body.changedArticles(), "무변경 제19조는 빠진다");
        assertEquals(List.of(clause), body.addenda());
        assertEquals(List.of(), body.delegationClauses());
        assertEquals(BASELINE_EF, body.baselineEffectiveDate());
    }

    @Test
    @DisplayName("시행된 버전 — 변경 유형은 두되 현행 대비·기준선 시행일 없음, 인용키에 날짜 없음")
    void 시행된_버전() {
        Law law = CitedSourceResolverTest.law(Law.Status.시행중, EF, "① 통합심의한다.");

        LawDetailResponse body = mapper.toBody(law, null, null);

        assertEquals("시행중", body.status());
        ChangedArticle article = body.changedArticles().get(0);
        assertEquals("LAW:001809:art:18", article.sourceId());
        assertEquals("개정", article.changeType());
        assertNull(article.previousText());
        assertNull(body.baselineEffectiveDate());
    }

    @Test
    @DisplayName("기준선이 없는 시행예정본(제정 등) — 현행 대비 없음")
    void 기준선_없음() {
        Law law = CitedSourceResolverTest.law(Law.Status.시행예정, EF, "① 통합심의한다.");

        LawDetailResponse body = mapper.toBody(law, null, null);

        assertNull(body.changedArticles().get(0).previousText());
        assertNull(body.baselineEffectiveDate());
    }

    @Test
    @DisplayName("요약 — 시행예정본이면 문단·주요 내용(인용)·불확실한 점, 시행된 버전은 싣지 않음")
    void 요약() {
        ImpactResult summary = new ImpactResult("LAW:001809@2026-11-01", "SUMMARY", "절차가 짧아져요.",
                List.of(new ImpactResult.Claim("통합심의가 넓어진다", List.of("LAW:001809@2026-11-01:art:18"), 0.9)),
                List.of(), List.of(), null, List.of("세부 기준은 대통령령"), "참고용", null);

        LawDetailResponse pending = mapper.toBody(
                CitedSourceResolverTest.law(Law.Status.시행예정, EF, "① 통합심의한다."), null, summary);
        assertEquals(new LawDetailResponse.LawSummary("절차가 짧아져요.",
                List.of(new LawDetailResponse.SummaryClaim("통합심의가 넓어진다", List.of("LAW:001809@2026-11-01:art:18"))),
                List.of("세부 기준은 대통령령")), pending.summary());

        LawDetailResponse inForce = mapper.toBody(
                CitedSourceResolverTest.law(Law.Status.시행중, EF, "① 통합심의한다."), null, summary);
        assertNull(inForce.summary());
        assertNull(mapper.toBody(CitedSourceResolverTest.law(Law.Status.시행예정, EF, "①"), null, null).summary());
    }
}
