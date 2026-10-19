package com.lia.core.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** 결정적 가짜 임베딩 — 같은 텍스트는 같은 단위 벡터. 실 API·비용 없이 PgVectorStore 를 돌린다. */
public class FakeEmbeddingModel implements EmbeddingModel {

    private final int dim;

    public FakeEmbeddingModel(int dim) {
        this.dim = dim;
    }

    @Override
    public float[] embed(Document document) {
        return vec(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            out.add(new Embedding(vec(texts.get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public int dimensions() {
        return dim;
    }

    private float[] vec(String text) {
        Random random = new Random(text.hashCode());
        float[] v = new float[dim];
        double sum = 0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (random.nextDouble() * 2 - 1);
            sum += v[i] * (double) v[i];
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }
}
