package com.lia.core.eval;

/** 평가 당시 법령 발견 설정(D83) — RED 단계 껍데기. */
public record RagConfig(int chunkFetch, int maxCandidates, double minSimilarity) {}
