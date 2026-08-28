package com.lia.core.pipeline.embed;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * OpenAI 임베딩 구현체 — Spring AI {@link EmbeddingModel} 위임(D32, `text-embedding-3-small`).
 *
 * <p>얕은 위임: 재시도는 Spring AI 기본, 과대 입력 분할은 호출자(RAGIndexer) 책임(D48 측정 선행).
 * 우리 몫은 <b>순서·dim·정규화</b> 보장뿐. OpenAI는 대칭 모델이라 {@link Mode}를 흡수(무시)한다.
 *
 * <p><b>계측은 Spring AI 내장에 위임한다.</b> {@code OpenAiEmbeddingModel} 은 ObservationRegistry 빈이
 * 있으면 자동으로 {@code gen_ai.client.operation}(타이머+span, OTel GenAI 컨벤션 — 모델·차원·토큰 태그)을
 * 낸다. 여기서 {@code lia.embed} 로 다시 감싸면 이중계측일 뿐이라 두지 않는다(normalize/diff 는 외부계측이
 * 없는 순수 CPU 라 {@code lia.*} 를 유지하는 것과 대비).
 */
public class OpenAiEmbedder implements Embedder {

    private final EmbeddingModel model;
    private final EmbeddingProperties props;

    public OpenAiEmbedder(EmbeddingModel model, EmbeddingProperties props) {
        this.model = model;
        this.props = props;
    }

    @Override
    public int dim() {
        return props.dim();
    }

    @Override
    public List<float[]> embed(List<String> texts, Mode mode) {
        // OpenAI 대칭 모델 — mode 무시. 순서대로 벡터 반환. (API 호출 계측은 Spring AI gen_ai.*)
        List<float[]> raw = model.embed(texts);
        if (!raw.isEmpty() && raw.get(0).length != props.dim()) {
            throw new IllegalStateException(
                    "임베딩 차원 불일치 — 기대 %d, 실제 %d. 모델·설정(lia.embedding.dim) 확인"
                            .formatted(props.dim(), raw.get(0).length));
        }
        return raw.stream().map(OpenAiEmbedder::normalize).toList();
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
