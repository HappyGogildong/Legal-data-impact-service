package com.lia.core.pipeline.embed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import com.lia.core.pipeline.embed.Embedder.Mode;

/**
 * OpenAiEmbedder 단위 — 외부 API 경계({@link EmbeddingModel})는 목으로 두고
 * <b>우리 로직</b>(정규화·순서·dim 가드·mode 흡수)만 검증한다. 실 호출 없음.
 */
class OpenAiEmbedderTest {

    private Embedder embedderReturning(int dim, List<float[]> vectors) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenReturn(vectors);
        return new OpenAiEmbedder(model, new EmbeddingProperties(dim));
    }

    @Test
    void 비정규화_벡터를_단위벡터로_정규화하고_순서를_보존한다() {
        // [3,4](길이 5) → [0.6,0.8], [8,6](길이 10) → [0.8,0.6]
        Embedder embedder = embedderReturning(2, List.of(new float[]{3, 4}, new float[]{8, 6}));

        List<float[]> out = embedder.embed(List.of("첫째", "둘째"), Mode.PASSAGE);

        assertEquals(2, out.size(), "입력 개수만큼 반환");
        assertArrayEquals(new float[]{0.6f, 0.8f}, out.get(0), 1e-6f);
        assertArrayEquals(new float[]{0.8f, 0.6f}, out.get(1), 1e-6f);
        assertEquals(1.0, norm(out.get(0)), 1e-6, "단위 정규화");
    }

    @Test
    void 응답_차원이_설정_dim과_다르면_예외() {
        // 설정 dim=1536 인데 모델이 길이 2 벡터를 반환 → 색인 붕괴 전에 조기 실패
        Embedder embedder = embedderReturning(1536, List.of(new float[]{1, 0}));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> embedder.embed(List.of("x"), Mode.QUERY));
        assertTrue(e.getMessage().contains("1536"), "메시지에 기대 차원 표기: " + e.getMessage());
    }

    @Test
    void mode는_무시된다_대칭모델() {
        Embedder embedder = embedderReturning(2, List.of(new float[]{3, 4}));
        assertArrayEquals(embedder.embed("t", Mode.PASSAGE), embedder.embed("t", Mode.QUERY), 1e-6f);
    }

    private static double norm(float[] v) {
        double s = 0;
        for (float x : v) s += x * (double) x;
        return Math.sqrt(s);
    }
}
