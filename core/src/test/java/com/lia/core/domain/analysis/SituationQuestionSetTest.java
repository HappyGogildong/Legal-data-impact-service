package com.lia.core.domain.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.SituationQuestion.AnswerType;

class SituationQuestionSetTest {

    private static final String BASIS = "LAW:001809@2026-08-04:art:18";

    private static SituationQuestion question(String id, String text, AnswerType type, List<String> options) {
        return new SituationQuestion(id, text, BASIS, type, options);
    }

    private static List<SituationQuestion> baseSet() {
        return List.of(
                question("q1", "계약을 이미 했나요?", AnswerType.YES_NO, List.of()),
                question("q2", "어느 쪽인가요?", AnswerType.CHOICE, List.of("가", "나")));
    }

    @Test
    @DisplayName("같은 질문 목록은 언제나 같은 식별자 — 16자리 소문자 16진수")
    void 결정론적() {
        String first = SituationQuestionSet.idOf(baseSet());

        assertEquals(first, SituationQuestionSet.idOf(baseSet()));
        assertTrue(first.matches("[0-9a-f]{16}"), first);
    }

    @Test
    @DisplayName("질문 문장·근거·형식·선택지·순서·id 중 하나라도 바뀌면 식별자가 달라진다")
    void 바뀌면_달라진다() {
        String base = SituationQuestionSet.idOf(baseSet());

        assertNotEquals(base, SituationQuestionSet.idOf(List.of(
                question("q1", "계약을 아직 안 했나요?", AnswerType.YES_NO, List.of()), baseSet().get(1))), "text");
        assertNotEquals(base, SituationQuestionSet.idOf(List.of(baseSet().get(0),
                new SituationQuestion("q2", "어느 쪽인가요?", BASIS + "x", AnswerType.CHOICE, List.of("가", "나")))), "basis");
        assertNotEquals(base, SituationQuestionSet.idOf(List.of(baseSet().get(0),
                question("q2", "어느 쪽인가요?", AnswerType.DATE, List.of()))), "answerType");
        assertNotEquals(base, SituationQuestionSet.idOf(List.of(baseSet().get(0),
                question("q2", "어느 쪽인가요?", AnswerType.CHOICE, List.of("가", "다")))), "options");
        assertNotEquals(base, SituationQuestionSet.idOf(List.of(baseSet().get(1), baseSet().get(0))), "order");
        assertNotEquals(base, SituationQuestionSet.idOf(List.of(baseSet().get(0).withId("q9"), baseSet().get(1))), "id");
    }

    @Test
    @DisplayName("경계가 모호한 문자열 이어붙이기로 충돌하지 않는다")
    void 경계가_모호하지_않다() {
        assertNotEquals(
                SituationQuestionSet.idOf(List.of(question("q1", "ab", AnswerType.CHOICE, List.of("c", "d")))),
                SituationQuestionSet.idOf(List.of(question("q1", "a", AnswerType.CHOICE, List.of("bc", "d")))));
    }
}
