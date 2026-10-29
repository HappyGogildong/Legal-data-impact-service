package com.lia.core.pipeline.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;

/**
 * 개인화 모델 출력 형식(D71) — {@code ImpactResult} 의 필드에 단계별 {@code situationNote}·{@code situationQuestions} 를 더한 것.
 * {@code ImpactResult} 를 바꾸지 않는 이유: 그 형식 지시는 Layer A(쉬운 요약 저장본)와 공유된다. 받은 뒤 기존 형식 + 상황 정보로 나눈다.
 * Jackson·Spring AI 구조화 출력이 쓰므로 public.
 */
public record PersonalizedReply(String lawRef, String command, String summary,
                                List<ImpactResult.Claim> claims, List<ImpactResult.Impact> impacts,
                                List<NotedAction> actions, ImpactResult.EffectiveInfo effectiveInfo,
                                List<String> uncertainties, String disclaimer,
                                List<QuestionDraft> situationQuestions) {

    /** 대응 한 단계 + 상황이 영향을 줬다면 그 이유 한 줄. */
    public record NotedAction(String what, String deadline, List<String> basis, String situationNote) {}

    /** 상황 질문 초안 — id 는 엔진이 필터 후 다시 붙인다. */
    public record QuestionDraft(String text, String basis, SituationQuestion.AnswerType answerType, List<String> options) {}

    /**
     * 기존 형식 결과(meta 없음 — 추론기가 찍는다) + 상황 질문(임시 id q1…) + 단계 설명.
     * 모델 JSON 에 null 항목이 섞여도 터지지 않게 null 단계·초안·선택지 항목은 버린다.
     */
    public PersonalizedOutput toOutput() {
        List<ImpactResult.Action> plainActions = null;
        List<StepNote> notes = new ArrayList<>();
        if (actions != null) {
            plainActions = new ArrayList<>();
            for (NotedAction action : actions) {
                if (action == null) continue;
                plainActions.add(new ImpactResult.Action(action.what(), action.deadline(), action.basis()));
                if (action.situationNote() != null && !action.situationNote().isBlank()) {
                    notes.add(new StepNote(plainActions.size() - 1, action.situationNote().strip()));
                }
            }
        }
        List<SituationQuestion> questions = new ArrayList<>();
        if (situationQuestions != null) {
            for (QuestionDraft draft : situationQuestions) {
                if (draft == null) continue;
                List<String> options = draft.options() == null ? null
                        : draft.options().stream().filter(Objects::nonNull).toList();
                questions.add(new SituationQuestion("q" + (questions.size() + 1),
                        draft.text() == null ? null : draft.text().strip(), draft.basis(), draft.answerType(), options));
            }
        }
        ImpactResult result = new ImpactResult(lawRef, command, summary, claims, impacts, plainActions,
                effectiveInfo, uncertainties, disclaimer, null);
        return new PersonalizedOutput(result, questions, notes);
    }
}
