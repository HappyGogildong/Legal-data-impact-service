package com.lia.core.store;

import java.util.Map;

/**
 * 벡터 색인 단위 — [[RAGIndexer]]가 만들고 [[ChunkStore]]가 저장한다.
 *
 * <p>벡터를 담지 않는다: 임베딩은 {@link ChunkStore} 안에서 PgVectorStore가 {@code content}로 수행.
 * {@code sourceId}가 검색↔정본을 잇는 그라운딩 키.
 *
 * @param sourceId 인용키 (조문 {@code LAW:{lawId}@{efYd}:art:{no}} · 요약 {@code LAW:{lawId}@{efYd}})
 * @param content  임베딩·검색 대상 텍스트
 * @param metadata 필터·역추적용 (kind·namespace·lawId·efYd·changed·articleNo)
 */
public record Chunk(String sourceId, String content, Map<String, Object> metadata) {}
