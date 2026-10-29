package com.lia.core.domain.analysis;

/** 상황이 영향을 준 대응 단계의 한 줄 설명(D71) — {@code index} 는 그 결과 {@code actions} 안의 위치(0부터). */
public record StepNote(int index, String note) {}
