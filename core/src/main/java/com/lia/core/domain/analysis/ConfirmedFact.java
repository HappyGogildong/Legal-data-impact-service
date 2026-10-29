package com.lia.core.domain.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 확인된 상황 사실(D71) — 재생성 프롬프트의 {@code [SITUATION]} 한 줄("질문: 답"). 근거가 아니므로 인용 대상이 아니다.
 */
public record ConfirmedFact(String question, String answer) {

    /**
     * 보관된 질문에 대한 답을 검증해 사실로 묶는다. "모름" 은 뺀다.
     * 정상 화면으로는 생길 수 없는 입력(모르는 질문·중복·형식 위반·빈 목록·전부 모름)은 {@link IllegalArgumentException}(400).
     */
    public static List<ConfirmedFact> confirm(List<SituationQuestion> questions, List<SituationAnswer> answers) {
        if (answers == null || answers.isEmpty()) throw new IllegalArgumentException("답이 없습니다.");
        Map<String, SituationQuestion> byId = questions.stream()
                .collect(Collectors.toMap(SituationQuestion::id, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<ConfirmedFact> facts = new ArrayList<>();
        for (SituationAnswer answer : answers) {
            SituationQuestion question = answer == null ? null : byId.get(answer.questionId());
            if (question == null) throw new IllegalArgumentException("알 수 없는 상황 질문입니다.");
            if (!seen.add(question.id())) throw new IllegalArgumentException("같은 질문에 답이 두 번 왔습니다.");
            if (!question.accepts(answer.answer())) throw new IllegalArgumentException("답 형식이 맞지 않습니다: " + question.id());
            if (!SituationQuestion.UNKNOWN.equals(answer.answer())) {
                facts.add(new ConfirmedFact(question.text(), answer.answer()));
            }
        }
        if (facts.isEmpty()) throw new IllegalArgumentException("확인된 상황이 없습니다(모두 모름).");
        return List.copyOf(facts);
    }
}
