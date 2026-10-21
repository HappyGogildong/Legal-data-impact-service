package com.lia.core.pipeline.batch;

/**
 * 배치 작업 종류(D62). SYNC=fetch→정규화→diff→저장 · INDEX=정본→청킹→임베딩 ·
 * TRANSITION=시행 전환 · PURGE=사라진 시행예정본 정리.
 */
public enum TaskKind { SYNC, INDEX, TRANSITION, PURGE }
