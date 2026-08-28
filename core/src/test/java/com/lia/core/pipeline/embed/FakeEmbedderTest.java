package com.lia.core.pipeline.embed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.embed.Embedder.Mode;

/**
 * FakeEmbedder 계약 — 실 API 없이 하류 테스트(eval·RAGIndexer)가 의존하므로
 * <b>결정론·dim·정규화·순서</b>를 보장해야 한다.
 */
class FakeEmbedderTest {

    private final Embedder fake = new FakeEmbedder(8);

    @Test
    void 같은_텍스트는_같은_벡터_결정론() {
        assertArrayEquals(fake.embed("주택법", Mode.PASSAGE), fake.embed("주택법", Mode.QUERY), 0f);
    }

    @Test
    void dim_길이와_단위_정규화() {
        float[] v = fake.embed("임대차", Mode.QUERY);
        assertEquals(8, v.length);
        double n = 0;
        for (float x : v) n += x * (double) x;
        assertEquals(1.0, Math.sqrt(n), 1e-6);
    }

    @Test
    void 다른_텍스트는_다른_벡터() {
        assertFalse(java.util.Arrays.equals(
                fake.embed("주택법", Mode.PASSAGE), fake.embed("근로기준법", Mode.PASSAGE)));
    }

    @Test
    void 순서를_보존한다() {
        List<float[]> out = fake.embed(List.of("가", "나"), Mode.PASSAGE);
        assertEquals(2, out.size());
        assertArrayEquals(fake.embed("가", Mode.PASSAGE), out.get(0), 0f);
        assertArrayEquals(fake.embed("나", Mode.PASSAGE), out.get(1), 0f);
    }
}
