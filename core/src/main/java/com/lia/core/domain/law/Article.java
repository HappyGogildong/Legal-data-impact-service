package com.lia.core.domain.law;

import java.time.LocalDate;

/**
 * 조문 — 법령 컨텍스트의 도메인 타입.
 *
 * <p>필드는 국가법령정보 {@code 조문단위} 실측 계약에 대응한다.
 * 명세: docs/components/component-specs.md §1.2
 */
public record Article(
        String no,                      // 조문번호 ("18")
        String title,                   // 조문제목
        String text,                    // ⚠️ 조문내용 + 항/호/목 재귀 병합 결과
        boolean changed,                // ★ 조문변경여부 — 이번 개정으로 바뀐 조문인가
        ChangeType changeType,
        String movedFrom,               // 조문이동이전
        String movedTo,                 // 조문이동이후
        LocalDate articleEffectiveDate, // 조문시행일자 (단계적 시행 시 조문별로 다름)
        boolean isArticle,              // 조문여부 — false 면 장·절 제목 등
        String diffVsCurrent            // 시행중본 대조 결과 (Diff Builder 가 채움)
) {
    /**
     * 신설·삭제는 <b>여기서 판정하지 않는다.</b> 기준선(시행중본) 없이는 알 수 없기 때문이다.
     * Normalizer 는 {@code 개정}/{@code 이동}/{@code 없음}까지만 정하고,
     * 신설·삭제 확정은 Diff Builder 가 조문번호 대조로 수행한다.
     */
    public enum ChangeType { 신설, 개정, 삭제, 이동, 없음 }

    /** 인용 표기용 조문 라벨 — "제18조". */
    public String label() {
        return no == null ? "" : "제" + no + "조";
    }

    /**
     * Diff Builder 가 기준선 대조 결과를 반영한 사본.
     * {@code changeType} 을 신설·삭제로 확정하고 {@code diffVsCurrent} 를 채운다.
     */
    public Article withDiff(ChangeType confirmedType, String diff) {
        return new Article(no, title, text, changed, confirmedType, movedFrom, movedTo,
                articleEffectiveDate, isArticle, diff);
    }
}
