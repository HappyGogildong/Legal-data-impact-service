package com.lia.core.pipeline.analyze;

import java.util.List;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;

/** 개인화 추론 결과(D71) — 기존 형식의 결과 + 상황 질문 + 단계 설명. 검증·필터 전 날것. */
public record PersonalizedOutput(ImpactResult result, List<SituationQuestion> questions, List<StepNote> stepNotes) {
    public PersonalizedOutput {
        questions = questions == null ? List.of() : List.copyOf(questions);
        stepNotes = stepNotes == null ? List.of() : List.copyOf(stepNotes);
    }
}
