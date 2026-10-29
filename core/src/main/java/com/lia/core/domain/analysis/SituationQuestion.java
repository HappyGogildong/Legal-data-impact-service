package com.lia.core.domain.analysis;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;

/**
 * 대응 방안 상황 질문(D71) — 사용자 상황에 따라 대응이 갈리는 단계를 가르는 질문 하나. 근거 조문({@code basis})이 있다.
 * 답은 닫힌 형식만 — 예/아니오/모름 · 날짜 · 선택지(2~4개). 모든 형식에 "모름" 을 허용한다.
 */
public record SituationQuestion(String id, String text, String basis, AnswerType answerType, List<String> options) {

    public enum AnswerType { YES_NO, DATE, CHOICE }

    public static final String UNKNOWN = "모름";
    public static final String YES = "예";
    public static final String NO = "아니오";
    public static final int MAX_TEXT = 200;
    public static final int MAX_OPTION = 40;

    public SituationQuestion {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public SituationQuestion withId(String newId) {
        return new SituationQuestion(newId, text, basis, answerType, options);
    }

    /** 화면에 그대로 보여 줄 수 있는 질문인가 — 한 줄·길이·근거·형식·선택지 개수. */
    public boolean wellFormed() {
        if (!singleLine(text, MAX_TEXT) || basis == null || basis.isBlank() || answerType == null) return false;
        if (answerType != AnswerType.CHOICE) return true;
        return options.size() >= 2 && options.size() <= 4
                && options.stream().allMatch(o -> singleLine(o, MAX_OPTION))
                && new HashSet<>(options).size() == options.size();
    }

    /** 이 질문에 허용된 답인가. */
    public boolean accepts(String answer) {
        if (answer == null) return false;
        if (UNKNOWN.equals(answer)) return true;
        return switch (answerType) {
            case YES_NO -> YES.equals(answer) || NO.equals(answer);
            case DATE -> isIsoDate(answer);
            case CHOICE -> options.contains(answer);
        };
    }

    private static boolean singleLine(String value, int maxChars) {
        return value != null && !value.isBlank() && value.length() <= maxChars
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    private static boolean isIsoDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
