package com.lia.core.domain.law;

/**
 * 조문 — 법령 컨텍스트의 도메인 타입.
 *
 * <p>필드는 #5 Normalizer 에서 실측 계약(조문변경여부·조문이동·조문시행일자)에 맞춰
 * 확장한다. 명세: docs/components/component-specs.md §1.2
 */
public record Article(
        String no,            // 예: "제12조"
        String title,
        String text,
        ChangeType changeType,
        String diffVsCurrent
) {
    public enum ChangeType { 신설, 개정, 삭제 }
}
