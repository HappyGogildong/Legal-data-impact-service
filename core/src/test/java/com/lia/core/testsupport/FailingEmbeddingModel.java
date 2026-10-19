package com.lia.core.testsupport;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** 항상 실패하는 임베딩 — 색인 교체 중 실패 시 롤백을 검증한다. */
public class FailingEmbeddingModel implements EmbeddingModel {

    private final int dim;

    public FailingEmbeddingModel(int dim) {
        this.dim = dim;
    }

    @Override
    public float[] embed(Document document) {
        throw new IllegalStateException("임베딩 실패(테스트)");
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        throw new IllegalStateException("임베딩 실패(테스트)");
    }

    @Override
    public int dimensions() {
        return dim;
    }
}
