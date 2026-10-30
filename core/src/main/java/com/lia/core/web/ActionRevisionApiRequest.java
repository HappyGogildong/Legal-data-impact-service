package com.lia.core.web;

import java.util.List;

import com.lia.core.domain.analysis.SituationAnswer;

/**
 * {@code POST /api/v1/analyses/action-revisions} 요청(D71) — 어떤 법령 버전의, 어떤 질문 묶음({@code setId})에 어떤 답을 했는지.
 *
 * @param setId 화면이 받은 {@code situation.setId} — 필수. 서버 세션의 질문 묶음과 다르면 만료로 본다.
 * @param analysisId 선택 — 분석 응답의 값. 재생성이 성공하고 같은 법령이면 그 결과에 재생성본·답을 붙인다(D73)
 */
public record ActionRevisionApiRequest(AnalyzeApiRequest.LawRefDto lawRef, String setId, List<SituationAnswer> answers,
                                       String analysisId) {}
