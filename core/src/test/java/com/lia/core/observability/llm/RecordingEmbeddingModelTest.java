package com.lia.core.observability.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

/** RecordingEmbeddingModel — 임베딩 진입점 모두가 호출 1회당 한 행을 남기는지. 실 API 없음. */
class RecordingEmbeddingModelTest {

    private final List<LlmCall> calls = new ArrayList<>();
    private final AtomicInteger apiCalls = new AtomicInteger();

    /** 입력 1건당 토큰 7개를 쓰는 가짜 OpenAI. */
    private final EmbeddingModel api = new EmbeddingModel() {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            apiCalls.incrementAndGet();
            List<Embedding> out = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) out.add(new Embedding(new float[] {1f, 0f}, i));
            int tokens = 7 * request.getInstructions().size();
            return new EmbeddingResponse(out, new EmbeddingResponseMetadata("text-embedding-3-small",
                    new DefaultUsage(tokens, 0, tokens)));
        }

        @Override
        public float[] embed(Document document) {
            throw new AssertionError("감싼 쪽이 call 로 보내야 한다");
        }

        @Override
        public int dimensions() {
            return 2;
        }
    };

    private final RecordingEmbeddingModel model = new RecordingEmbeddingModel(api, LlmPurpose.INDEX_EMBED, calls::add);

    @Test
    void 묶음_호출_한_번에_한_행과_입력_토큰() {
        model.embed(List.of("가", "나", "다"));

        assertEquals(1, calls.size());
        LlmCall call = calls.get(0);
        assertEquals(LlmPurpose.INDEX_EMBED, call.purpose());
        assertEquals("text-embedding-3-small", call.model());
        assertEquals(21, call.usage().inputTokens());
        assertTrue(call.succeeded());
        assertNull(call.groupKey());
    }

    @Test
    void 문서_하나를_임베딩해도_기록된다() {
        float[] vector = model.embed(new Document("조문"));

        assertArrayEquals(new float[] {1f, 0f}, vector);
        assertEquals(1, calls.size());
        assertEquals(7, calls.get(0).usage().inputTokens());
    }

    @Test
    void 문자열_하나도_기록된다() {
        model.embed("질의");

        assertEquals(1, calls.size());
    }

    @Test
    void 차원은_유료_호출_없이_위임한다() {
        assertEquals(2, model.dimensions());
        assertEquals(0, apiCalls.get());
        assertTrue(calls.isEmpty());
    }

    @Test
    void 실패하면_실패_행을_남기고_같은_예외를_다시_던진다() {
        RuntimeException boom = new IllegalStateException("429");
        EmbeddingModel failing = new EmbeddingModel() {
            public EmbeddingResponse call(EmbeddingRequest request) { throw boom; }
            public float[] embed(Document document) { throw new AssertionError(); }
        };
        RecordingEmbeddingModel recording = new RecordingEmbeddingModel(failing, LlmPurpose.DISCOVERY_EMBED, calls::add);

        assertSame(boom, assertThrows(RuntimeException.class, () -> recording.embed("질의")));

        assertEquals(1, calls.size());
        assertFalse(calls.get(0).succeeded());
        assertEquals(LlmPurpose.DISCOVERY_EMBED, calls.get(0).purpose());
    }
}
