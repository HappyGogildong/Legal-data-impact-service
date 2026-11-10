package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import com.lia.core.observability.llm.LlmPurpose;
import com.lia.core.observability.llm.RecordingEmbeddingModel;
import com.lia.core.pipeline.batch.IngestJob;
import com.lia.core.pipeline.batch.IngestJobRunner;

/**
 * batch 프로파일 컨텍스트 — 웹 서버 없이 뜨고(보안 필터체인 없음), 배치 빈이 배선된다.
 * 인자 없이 뜨므로 러너는 "명령 없음"으로 종료 코드 1을 내고 DB 에는 접근하지 않는다(DB 불필요).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("batch")
class BatchContextTest {

    @Autowired ApplicationContext context;

    @Test
    @DisplayName("배치 빈 배선 + 웹 전용 보안 설정은 빠진다")
    void 배선() {
        assertNotNull(context.getBean(IngestJob.class));
        IngestJobRunner runner = context.getBean(IngestJobRunner.class);
        assertEquals(1, runner.getExitCode(), "명령 없이 실행 → 1");
        assertTrue(context.getBeansOfType(SecurityFilterChain.class).isEmpty(), "웹이 아니면 SecurityConfig 미로드");
    }

    @Test
    @DisplayName("배치의 임베딩은 색인 목적으로 기록된다(D83)")
    void 임베딩_기록_목적() {
        EmbeddingModel model = context.getBean(EmbeddingModel.class);
        assertInstanceOf(RecordingEmbeddingModel.class, model, "PgVectorStore 가 쓰는 임베딩이 기록 데코레이터");
        assertEquals(LlmPurpose.INDEX_EMBED, ((RecordingEmbeddingModel) model).purpose());
    }
}
