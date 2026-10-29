package com.lia.core.domain.analysis;

import java.util.List;

/** Layer B 결과에 딸린 상황 정보(D71) — 상황 질문과 단계 설명. 개인화 답이라 DB 에 두지 않는다. */
public record Situation(List<SituationQuestion> questions, List<StepNote> stepNotes) {

    public Situation {
        questions = questions == null ? List.of() : List.copyOf(questions);
        stepNotes = stepNotes == null ? List.of() : List.copyOf(stepNotes);
    }

    public static Situation none() {
        return new Situation(List.of(), List.of());
    }

    public boolean isEmpty() {
        return questions.isEmpty() && stepNotes.isEmpty();
    }
}
