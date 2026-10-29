package com.lia.core.domain.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 질문 묶음 식별(D71) — 화면이 보여 준 질문 묶음과 서버 세션이 들고 있는 묶음이 같은지 가르는 값.
 * 질문 내용(id·문장·근거·형식·선택지)과 순서에서 결정론적으로 계산한다 — 상태를 두지 않으므로
 * 응답 매퍼({@code situation.setId})와 재생성 유스케이스가 이 함수 하나로 같은 값을 얻는다.
 * 답이나 사용자 정보는 입력에 들어가지 않는다.
 */
public final class SituationQuestionSet {

    /** SHA-256 앞 16 hex(64비트) — 한 세션 안 법령 20개 남짓의 묶음을 가르는 데는 충분하다. */
    static final int HEX_LENGTH = 16;

    private SituationQuestionSet() {}

    public static String idOf(List<SituationQuestion> questions) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(questions).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);   // JRE 가 항상 제공하는 알고리즘
        }
    }

    /** 필드마다 "길이:값" — 값 이어붙임으로 경계가 모호해져 서로 다른 묶음이 같은 문자열이 되는 일이 없다. */
    private static String canonical(List<SituationQuestion> questions) {
        StringBuilder text = new StringBuilder();
        for (SituationQuestion question : questions) {
            field(text, question.id());
            field(text, question.text());
            field(text, question.basis());
            field(text, question.answerType() == null ? null : question.answerType().name());
            text.append('[').append(question.options().size()).append(']');
            question.options().forEach(option -> field(text, option));
            text.append(';');
        }
        return text.toString();
    }

    private static void field(StringBuilder text, String value) {
        text.append(value == null ? "-" : value.length() + ":" + value).append('|');
    }
}
