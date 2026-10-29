package com.lia.core.application.analysis;

import java.util.List;
import java.util.Optional;

import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.pipeline.plan.LawRef;

/**
 * 상황 질문 보관 포트(D71) — 첫 Layer B 응답의 질문을 법령 버전({@code lawId@시행일}) 단위로 잠시 둔다.
 * 구현은 web 계층의 HTTP 세션 어댑터(메모리, DB 아님). 답은 두지 않는다.
 */
public interface SituationQuestions {
    Optional<List<SituationQuestion>> find(LawRef ref);
    void save(LawRef ref, List<SituationQuestion> questions);
}
