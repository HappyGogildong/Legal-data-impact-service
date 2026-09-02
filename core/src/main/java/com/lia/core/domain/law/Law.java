package com.lia.core.domain.law;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 법령 — <b>MVP의 유일한 분석 대상</b>(D42).
 *
 * <p>시행중본과 시행예정본은 출처 응답 스키마가 같으므로 한 타입으로 담고
 * {@link Status} 로만 구분한다. 우리가 저작하지 않는 외부 권위 사실이라
 * 상태 전이 메서드는 없다 — 대신 <b>분석 파이프라인이 반복해서 묻는 질문</b>
 * (변경된 조문은? 실조문만? 인용키는?)을 메서드로 제공한다.
 *
 * <p>명세: docs/components/component-specs.md §1.1
 */
public record Law(
        String lawId,                   // ★ 법령ID (버전 불변) — 시행중↔시행예정 연결키
        String mst,                     // 법령일련번호 — 버전마다 다름. 연결키로 쓰지 말 것
        String title,
        Status status,
        AmendKind amendKind,
        LawType lawType,
        String ministry,
        LocalDate promulgateDate,
        String promulgateNo,            // 부칙 필터 키
        LocalDate effectiveDate,        // ★ 시행예정본은 미래일
        String effectiveRule,           // "공포 후 6개월이 경과한 날부터 시행한다..."
        EnforcementType enforcementType,
        String amendReason,             // 제개정이유
        String amendText,               // 개정문 — 자구 단위 개정 지시문
        List<Addendum> addenda,         // 이번 개정분만 (promulgateNo 로 필터됨)
        List<Article> articles,
        List<String> delegationClauses, // "대통령령으로 정한다" 등
        String baselineLawId,
        String sourceUrl,
        String revision,
        Instant lastSeen
) {
    public enum Status { 시행중, 시행예정 }

    public enum AmendKind { 제정, 일부개정, 전부개정, 타법개정, 폐지, 기타 }

    public enum LawType { 법률, 대통령령, 총리령, 부령, 기타 }

    /** 부칙 단서조항 유무로 판정한다. */
    public enum EnforcementType { 즉시, 유예, 단계적 }

    public Law {
        if (lawId == null || lawId.isBlank()) {
            throw new IllegalArgumentException("법령ID는 필수다 — 시행중↔시행예정 연결키.");
        }
        if (effectiveDate == null) {
            throw new IllegalArgumentException("시행일자는 필수다 — ActionPlan 기한 산출의 근거.");
        }
        addenda = addenda == null ? List.of() : List.copyOf(addenda);
        articles = articles == null ? List.of() : List.copyOf(articles);
        delegationClauses = delegationClauses == null ? List.of() : List.copyOf(delegationClauses);
    }

    // --- 도메인 질의 -----------------------------------------------------

    /**
     * <b>이번 개정으로 바뀐 조문만.</b> LawDiff·분석의 대상 선별이며 비용 레버다 —
     * 실측(주택법)에서 조문 137개 중 6개였다.
     */
    public List<Article> changedArticles() {
        return articles.stream().filter(Article::changed).toList();
    }

    /** 실조문만 (장·절 제목 등 전개 항목 제외). */
    public List<Article> realArticles() {
        return articles.stream().filter(Article::isArticle).toList();
    }

    public Article article(String no) {
        return articles.stream().filter(a -> a.no() != null && a.no().equals(no)).findFirst().orElse(null);
    }

    /** 시행일 조항 — `effectiveRule` 의 출처. */
    public Addendum effectiveClause() {
        return addenda.stream().filter(a -> a.kind() == Addendum.Kind.시행일).findFirst().orElse(null);
    }

    public boolean pending() {
        return status == Status.시행예정;
    }

    /** 아직 시행되지 않았는가 (기준일 대비). */
    public boolean notYetEffective(LocalDate asOf) {
        return effectiveDate.isAfter(asOf);
    }

    /**
     * 인용·캐시용 참조 키. <b>시행일을 포함한다</b> — 같은 법령ID에 시행예정본이
     * 복수일 수 있어 {@code lawId} 만으로는 특정되지 않는다(D43).
     */
    public String ref() {
        return pending() ? "LAW:" + lawId + "@" + effectiveDate : "LAW:" + lawId;
    }

    /** 조문 인용키 — `LAW:{lawId}@{efYd}:art:{no}`. */
    public String sourceId(Article article) {
        return ref() + ":art:" + article.no();
    }

    /** 개정문 인용키 — `LAW:{lawId}@{efYd}:amend`. */
    public String amendSourceId() {
        return ref() + ":amend";
    }

    /** 부칙 인용키 — `LAW:{lawId}@{efYd}:addenda:{no}`. */
    public String addendumSourceId(Addendum addendum) {
        return ref() + ":addenda:" + addendum.no();
    }

    /** 전문 — 실조문 본문 병합(저장 필드가 아니라 파생값). */
    public String fullText() {
        return realArticles().stream()
                .map(Article::text)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    /** 캐시 무효화 해시를 새로 계산해 교체한 사본. */
    public Law withRevision(String newRevision) {
        return new Law(lawId, mst, title, status, amendKind, lawType, ministry,
                promulgateDate, promulgateNo, effectiveDate, effectiveRule, enforcementType,
                amendReason, amendText, addenda, articles, delegationClauses,
                baselineLawId, sourceUrl, newRevision, lastSeen);
    }

    /** diff 기준선(시행중본) 연결. */
    public Law withBaseline(String baseline) {
        return new Law(lawId, mst, title, status, amendKind, lawType, ministry,
                promulgateDate, promulgateNo, effectiveDate, effectiveRule, enforcementType,
                amendReason, amendText, addenda, articles, delegationClauses,
                baseline, sourceUrl, revision, lastSeen);
    }

    /** 조문 목록 교체(Diff Builder 가 `diffVsCurrent` 를 채운 뒤 사용). */
    public Law withArticles(List<Article> newArticles) {
        return new Law(lawId, mst, title, status, amendKind, lawType, ministry,
                promulgateDate, promulgateNo, effectiveDate, effectiveRule, enforcementType,
                amendReason, amendText, addenda, new ArrayList<>(newArticles), delegationClauses,
                baselineLawId, sourceUrl, revision, lastSeen);
    }
}
