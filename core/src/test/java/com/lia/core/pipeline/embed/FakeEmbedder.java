package com.lia.core.pipeline.embed;

import java.util.List;
import java.util.Random;

/**
 * 결정론 Fake — 실 API 없이 하류(eval·RAGIndexer) 테스트가 쓰는 재사용 더블.
 *
 * <p>텍스트 해시로 시드한 난수 → dim 벡터 → 단위 정규화. 같은 텍스트는 항상 같은 벡터,
 * 다른 텍스트는 (거의 확실히) 다른 벡터. mode는 흡수(대칭). 네트워크·비용 없음.
 */
public class FakeEmbedder implements Embedder {

    private final int dim;

    public FakeEmbedder(int dim) {
        this.dim = dim;
    }

    @Override
    public int dim() {
        return dim;
    }

    @Override
    public List<float[]> embed(List<String> texts, Mode mode) {
        return texts.stream().map(this::vector).toList();
    }

    private float[] vector(String text) {
        Random rnd = new Random(text.hashCode());
        float[] v = new float[dim];
        double sum = 0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (rnd.nextDouble() * 2 - 1);
            sum += v[i] * (double) v[i];
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < dim; i++) v[i] = (float) (v[i] / norm);
        return v;
    }
}
