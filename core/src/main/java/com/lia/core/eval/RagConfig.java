package com.lia.core.eval;

/**
 * RAG 구성 노브 — <b>회귀 테스트가 한 번에 하나만 바꾸는 통제변인</b>(D33 §3).
 *
 * <p>이 값을 바꾸며 같은 골든셋을 돌려, 어떤 변경이 성능을 떨어뜨리는지 본다.
 * {@link #key()}는 baseline 저장·조회 키다.
 *
 * <p>설계: docs/eval/rag-evaluation-framework.md
 */
public record RagConfig(
        int chunkSize,
        String embeddingModel,
        int topK,
        boolean reranker,
        boolean queryRewrite,
        String promptVersion
) {
    /** baseline 파일 매칭용 안정 키. */
    public String key() {
        return "chunk%d.%s.k%d.rr%b.qr%b.p%s".formatted(
                chunkSize, embeddingModel, topK, reranker, queryRewrite, promptVersion);
    }

    /** 개발 기본값 — 조문 단위 청킹, OpenAI 1536, top-5(D33). */
    public static RagConfig defaults() {
        return new RagConfig(0, "text-embedding-3-small", 5, false, false, "0.2");
    }

    /** 노브 하나만 바꾼 사본(통제변인 스윕용). */
    public RagConfig withTopK(int k) {
        return new RagConfig(chunkSize, embeddingModel, k, reranker, queryRewrite, promptVersion);
    }

    public RagConfig withReranker(boolean on) {
        return new RagConfig(chunkSize, embeddingModel, topK, on, queryRewrite, promptVersion);
    }
}
