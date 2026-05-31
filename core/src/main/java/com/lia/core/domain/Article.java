package com.lia.core.domain;

/** 조문. */
public record Article(
        String no,            // 예: "제12조"
        String title,
        String text,
        ChangeType changeType,
        String diffVsCurrent
) {
    public enum ChangeType { 신설, 개정, 삭제 }
}
