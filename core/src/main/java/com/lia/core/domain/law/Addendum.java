package com.lia.core.domain.law;

import java.time.LocalDate;

/**
 * 부칙 조항 하나 — 시행일·경과조치·적용례·특례.
 *
 * <p>API 의 {@code 부칙단위} 는 개정 1건의 부칙 <b>전체</b>를 한 덩어리로 준다.
 * Normalizer 가 이를 {@code 제N조(제목)} 단위로 쪼개 이 타입으로 만든다.
 *
 * <p>⚠️ 부칙은 제정 이후 <b>이력 전체</b>가 오므로(실측 42개) 이번 개정분만
 * {@code 부칙공포번호 == 법령 공포번호} 로 걸러야 한다. 걸러지지 않으면 10년 전
 * 경과조치를 이번 개정 내용으로 오인한다.
 */
public record Addendum(
        String no,                  // "제1조"
        String title,               // "시행일"
        Kind kind,
        String text,
        String promulgateNo,        // 소속 개정의 공포번호
        LocalDate promulgateDate
) {
    public enum Kind { 시행일, 경과조치, 적용례, 특례, 기타 }

    /** 조문 제목으로 종류 판정. 분류 불가면 기타. */
    public static Kind kindOf(String title) {
        if (title == null) return Kind.기타;
        if (title.contains("시행일")) return Kind.시행일;
        if (title.contains("경과조치")) return Kind.경과조치;
        if (title.contains("적용례")) return Kind.적용례;
        if (title.contains("특례")) return Kind.특례;
        return Kind.기타;
    }
}
