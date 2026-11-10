package com.lia.core.observability.llm;

import java.time.Instant;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
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

    /** 호출 1회 = 기록 1행. 실패하면 실패 행을 남기고 같은 예외를 다시 던진다. */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Instant calledAt = Instant.now();
        EmbeddingResponse response;
        try {
            response = delegate.call(request);
        } catch (RuntimeException e) {
            recorder.record(LlmCall.single(calledAt, purpose, null, false, LlmUsage.NONE));
            throw e;
        }
        recorder.record(LlmCall.single(calledAt, purpose, null, true, LlmUsage.of(response)));
        return response;
    }

    @Override
    public float[] embed(Document document) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(getEmbeddingContent(document)),
                EmbeddingOptions.builder().build()));
        return response.getResults().isEmpty() ? new float[0] : response.getResults().get(0).getOutput();
    }

    /** 감싼 모델의 규칙(예: 메타데이터 포함 방식)을 그대로 쓴다. */
    @Override
    public String getEmbeddingContent(Document document) {
        return delegate.getEmbeddingContent(document);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    /** 기록할 목적 — 실행 모드로 정해진다(lia.llm-calls.embedding-purpose). */
    public LlmPurpose purpose() {
        return purpose;
    }
}
