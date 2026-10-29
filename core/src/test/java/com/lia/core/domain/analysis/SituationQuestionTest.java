package com.lia.core.domain.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class SituationQuestionTest {

    static SituationQuestion yesNo() {
        return new SituationQuestion("q1", "계약을 2026-11-01 이전에 맺었나요?", "LAW:001@2026-11-01:addenda:2",
                SituationQuestion.AnswerType.YES_NO, List.of());
    }

    static SituationQuestion choice(List<String> options) {
        return new SituationQuestion("q2", "연 소득이 어느 구간인가요?", "LAW:001@2026-11-01:art:55",
                SituationQuestion.AnswerType.CHOICE, options);
    }

    @Test
    void 예_아니오_질문은_예_아니오_모름만_받는다() {
        assertTrue(yesNo().accepts("예"));
        assertTrue(yesNo().accepts("아니오"));
        assertTrue(yesNo().accepts("모름"));
        assertFalse(yesNo().accepts("네"));
        assertFalse(yesNo().accepts(null));
    }

    @Test
    void 날짜_질문은_ISO_날짜나_모름만() {
        SituationQuestion date = new SituationQuestion("q1", "계약 만료일은?", "LAW:001@2026-11-01:art:6",
                SituationQuestion.AnswerType.DATE, List.of());
        assertTrue(date.accepts("2027-03-01"));
        assertTrue(date.accepts("모름"));
        assertFalse(date.accepts("2027-3-1"));
        assertFalse(date.accepts("2027-02-30"));
    }

    @Test
    void 선택지_질문은_선택지_중_하나나_모름만() {
        SituationQuestion q = choice(List.of("5천만 원 이하", "초과"));
        assertTrue(q.accepts("초과"));
        assertTrue(q.accepts("모름"));
        assertFalse(q.accepts("1억"));
    }

    @Test
    void 형식이_맞는_질문만_wellFormed() {
        assertTrue(yesNo().wellFormed());
        assertTrue(choice(List.of("이하", "초과")).wellFormed());
        assertFalse(choice(List.of("하나뿐")).wellFormed(), "선택지 2~4개");
        assertFalse(choice(List.of("a", "b", "c", "d", "e")).wellFormed());
        assertFalse(choice(List.of("같음", "같음")).wellFormed(), "중복 선택지");
        assertFalse(new SituationQuestion("q1", " ", "LAW:x", SituationQuestion.AnswerType.YES_NO, List.of()).wellFormed());
        assertFalse(new SituationQuestion("q1", "가".repeat(201), "LAW:x", SituationQuestion.AnswerType.YES_NO, List.of()).wellFormed());
        assertFalse(new SituationQuestion("q1", "두 줄\n질문", "LAW:x", SituationQuestion.AnswerType.YES_NO, List.of()).wellFormed());
        assertFalse(new SituationQuestion("q1", "질문", " ", SituationQuestion.AnswerType.YES_NO, List.of()).wellFormed());
        assertFalse(new SituationQuestion("q1", "질문", "LAW:x", null, List.of()).wellFormed());
    }

    @Test
    void withId_는_id_만_바꾼다() {
        SituationQuestion renamed = yesNo().withId("q3");
        assertEquals("q3", renamed.id());
        assertEquals(yesNo().text(), renamed.text());
    }

    @Test
    void options_null_은_빈_목록() {
        assertEquals(List.of(), new SituationQuestion("q1", "질문", "LAW:x", SituationQuestion.AnswerType.YES_NO, null).options());
    }
}
