package com.lia.core.domain.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConfirmedFactTest {

    final List<SituationQuestion> questions = List.of(
            SituationQuestionTest.yesNo(),
            SituationQuestionTest.choice(List.of("5천만 원 이하", "초과")));

    @Test
    void 답을_질문_문장과_묶고_모름은_뺀다() {
        List<ConfirmedFact> facts = ConfirmedFact.confirm(questions, List.of(
                new SituationAnswer("q1", "예"), new SituationAnswer("q2", "모름")));

        assertEquals(List.of(new ConfirmedFact("계약을 2026-11-01 이전에 맺었나요?", "예")), facts);
    }

    @Test
    void 모르는_질문_id_는_거부() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfirmedFact.confirm(questions, List.of(new SituationAnswer("q9", "예"))));
    }

    @Test
    void 형식에_맞지_않는_답은_거부() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfirmedFact.confirm(questions, List.of(new SituationAnswer("q2", "1억"))));
    }

    @Test
    void 같은_질문에_두_번_답하면_거부() {
        assertThrows(IllegalArgumentException.class, () -> ConfirmedFact.confirm(questions, List.of(
                new SituationAnswer("q1", "예"), new SituationAnswer("q1", "아니오"))));
    }

    @Test
    void 빈_답이나_전부_모름은_거부() {
        assertThrows(IllegalArgumentException.class, () -> ConfirmedFact.confirm(questions, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ConfirmedFact.confirm(questions, null));
        assertThrows(IllegalArgumentException.class,
                () -> ConfirmedFact.confirm(questions, List.of(new SituationAnswer("q1", "모름"))));
    }
}
