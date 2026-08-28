package com.lia.core.pipeline.embed;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.lia.core.observability.Obs;

/**
 * OpenAI 임베딩 구현체 — Spring AI {@link EmbeddingModel} 위임(D32, `text-embedding-3-small`).
 *
 * <p>얕은 위임: 재시도는 Spring AI 기본, 과대 입력 분할은 호출자(RAGIndexer) 책임(D48 측정 선행).
 * 우리 몫은 <b>순서·dim·정규화</b> 보장뿐. OpenAI는 대칭 모델이라 {@link Mode}를 흡수(무시)한다.
 */
public class OpenAiEmbedder implements Embedder {

    private final EmbeddingModel model;
    private final EmbeddingProperties props;
    /** 계측 레지스트리 — 미주입 시 NOOP(단위 테스트 무영향). */
    private final ObservationRegistry observations;

    public OpenAiEmbedder(EmbeddingModel model, EmbeddingProperties props) {
        this(model, props, ObservationRegistry.NOOP);
    }

    public OpenAiEmbedder(EmbeddingModel model, EmbeddingProperties props, ObservationRegistry observations) {
        this.model = model;
        this.props = props;
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
    }

    @Override
    public int dim() {
        return props.dim();
    }

    @Override
    public List<float[]> embed(List<String> texts, Mode mode) {
        return Observation.createNotStarted(Obs.EMBED, observations).observe(() -> {
            // OpenAI 대칭 모델 — mode 무시. 순서대로 벡터 반환.
            List<float[]> raw = model.embed(texts);
            if (!raw.isEmpty() && raw.get(0).length != props.dim()) {
                throw new IllegalStateException(
                        "임베딩 차원 불일치 — 기대 %d, 실제 %d. 모델·설정(lia.embedding.dim) 확인"
                                .formatted(props.dim(), raw.get(0).length));
            }
            return raw.stream().map(OpenAiEmbedder::normalize).toList();
        });
    }

    /** 코사인용 L2 단위 정규화(방어적 — 벤더 무관하게 불변식 보장). */
    private static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * (double) x;
        double norm = Math.sqrt(sum);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }
}
