package com.lia.core.observability.llm;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 임베딩 호출마다 기록을 남기는 {@link EmbeddingModel} 데코레이터. {@code PgVectorStore} 의 색인·검색 임베딩은
 * 모두 {@link #call} 을 지난다(기본 {@code embed(...)} 들이 {@code this.call} 로 모인다). 추상 메서드
 * {@link #embed(Document)} 도 {@link #call} 로 보내고, {@link #dimensions()} 는 위임한다 — 기본 구현은 차원을
 * 알아내려고 유료 호출을 한 번 한다.
 */
public class RecordingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final LlmPurpose purpose;
    private final LlmCallRecorder recorder;

    public RecordingEmbeddingModel(EmbeddingModel delegate, LlmPurpose purpose, LlmCallRecorder recorder) {
        this.delegate = delegate;
        this.purpose = purpose;
        this.recorder = recorder;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate.call(request);
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(document);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    /** 감싼 모델 — 설정·진단용. */
    public EmbeddingModel delegate() {
        return delegate;
    }
}
