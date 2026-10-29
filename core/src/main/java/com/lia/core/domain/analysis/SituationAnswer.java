package com.lia.core.domain.analysis;

/** 상황 질문에 대한 사용자 답(D71) — 질문 id 와 답 문자열만. */
public record SituationAnswer(String questionId, String answer) {}
