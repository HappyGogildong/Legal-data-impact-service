package com.lia.core.web;

import java.util.List;

import com.lia.core.domain.analysis.SituationAnswer;

/** {@code POST /api/v1/analyses/action-revisions} 요청(D71) — 어떤 법령 버전의 질문에 어떤 답을 했는지. */
public record ActionRevisionApiRequest(AnalyzeApiRequest.LawRefDto lawRef, List<SituationAnswer> answers) {}
