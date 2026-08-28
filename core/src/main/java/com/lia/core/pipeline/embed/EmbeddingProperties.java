package com.lia.core.pipeline.embed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 임베딩 설정 (application.yml {@code lia.embedding.*}).
 *
 * <p>{@code dim}은 <b>단일 소스</b>다 — Embedder 반환 차원이자
 * {@link com.lia.core.store.LawStore} chunks(pgvector) 차원. 한 곳에서 강제해 불일치(색인 붕괴)를 막는다.
 * 벤더/모델은 {@code spring.ai.openai.embedding} 에 둔다(D32).
 */
@ConfigurationProperties(prefix = "lia.embedding")
public record EmbeddingProperties(int dim) {

    public EmbeddingProperties {
        if (dim <= 0) dim = 1536;   // D32: OpenAI text-embedding-3-small = 1536
    }
}
