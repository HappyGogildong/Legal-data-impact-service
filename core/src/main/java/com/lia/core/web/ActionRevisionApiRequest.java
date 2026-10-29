package com.lia.core.web;

import java.util.List;

import com.lia.core.domain.analysis.SituationAnswer;

/**
 * {@code POST /api/v1/analyses/action-revisions} 요청(D71) — 어떤 법령 버전의, 어떤 질문 묶음({@code setId})에 어떤 답을 했는지.
 *
 * @param setId 화면이 받은 {@code situation.setId} — 필수. 서버 세션의 질문 묶음과 다르면 만료로 본다.
 */
public record ActionRevisionApiRequest(AnalyzeApiRequest.LawRefDto lawRef, String setId, List<SituationAnswer> answers) {}
