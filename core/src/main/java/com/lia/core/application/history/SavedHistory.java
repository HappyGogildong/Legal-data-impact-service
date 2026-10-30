package com.lia.core.application.history;

/** 이력 저장 결과 — {@code created} 가 false 면 같은 분석을 이미 저장해 둔 것(멱등). */
public record SavedHistory(long id, boolean created) {}
