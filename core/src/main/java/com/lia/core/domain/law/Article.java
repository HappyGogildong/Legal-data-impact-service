package com.lia.core.domain.law;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 한 조문을 가리키는 표기 — "18", "제105조의2", "제18조제1항"(항·호·목은 버린다). */
    private static final Pattern WRITTEN_NO = Pattern.compile("제?(\\d+)조?(의\\d+)?(?:제\\d+[항호목])*");

    /** 인용 표기용 조문 라벨 — "제18조", 가지조는 "제105조의2" ({@code no} 가 "105의2" 일 때). */
    public String label() {
        return labelOf(no);
    }

    /**
     * 조문번호({@code no} 형식 — "18", "105의2") → 인용 표기. 이동 전·후 번호({@code movedFrom}·{@code movedTo})도
     * 같은 형식이라 같은 표기를 쓴다. 번호가 없으면 빈 문자열.
     */
    public static String labelOf(String no) {
        if (no == null) return "";
        int branchAt = no.indexOf('의');
        return branchAt < 0 ? "제" + no + "조" : "제" + no.substring(0, branchAt) + "조" + no.substring(branchAt);
    }

    /**
     * 사람·번역기가 쓴 조문 표기 → {@code no} 형식("18", "105의2"). 한 조문으로 해석되지 않으면(여러 조문 포함) null.
     */
    public static String normalizeNo(String written) {
        if (written == null) return null;
        Matcher matcher = WRITTEN_NO.matcher(written.replaceAll("\\s+", ""));
        if (!matcher.matches()) return null;
        return matcher.group(2) == null ? matcher.group(1) : matcher.group(1) + matcher.group(2);
    }

    /**
     * 기준선(시행중본)에서 이 조문에 대응하는 조문 번호 — 옮겨진 조문은 옮기기 전 번호, 그 외는 같은 번호.
     * 대조(Diff Builder)·근거 조립(ContextBuilder)·조문 대비 화면이 같은 규칙을 써야 근거와 화면이 어긋나지 않는다.
     */
    public String baselineNo() {
        boolean moved = changeType == ChangeType.이동 && movedFrom != null && !movedFrom.isBlank();
        return moved ? movedFrom : no;
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
