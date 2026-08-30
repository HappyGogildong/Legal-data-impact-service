package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Law;
import com.lia.core.eval.Retriever.Retrieved;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.ChunkStore;

/**
 * RAG 검색 경로 <b>실 왕복 스모크</b> — 실 OpenAI 임베딩 + 실 pgvector로 적재→검색이 진짜 되는지 실측.
 * <b>비용 발생</b>. 기본 {@code ./gradlew test}에서 절대 안 돈다: 명시적 옵트인 {@code LIA_RAG_SMOKE=1}
 * (그때 {@code OPENAI_API_KEY} 필요) + Docker. 사용자가 직접:
 * <pre>LIA_RAG_SMOKE=1 ./gradlew test --tests "*RagSearchLiveSmokeTest"</pre>
 *
 * <p>단위·Fake는 "우리 로직"만 본다. 여기서는 실 임베딩으로 <b>적재한 법령이 토픽 질의로 검색되는가</b>를 증명.
 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "LIA_RAG_SMOKE", matches = "(?i)(1|true|yes)")
class RagSearchLiveSmokeTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "true");   // 실 스키마 생성
        r.add("spring.flyway.enabled", () -> "false");                              // law_versions 불필요
    }

    @Autowired RAGIndexer ragIndexer;
    @Autowired ChunkStore chunkStore;

    @Test
    void 적재한_법령이_토픽질의로_검색된다() {
        Law housing = new Normalizer().normalize(loadPending("samples/housing-act.json"));
        ragIndexer.index(housing);                                   // 실 OpenAI 임베딩으로 적재

        Retriever retriever = new ChunkStoreRetriever(chunkStore);
        List<Retrieved> hits = retriever.retrieve("주택 사업계획 통합심의가 어떻게 바뀌나", RagConfig.defaults());

        assertFalse(hits.isEmpty(), "검색 결과 없음 — 왕복 실패");
        assertTrue(hits.stream().anyMatch(h -> h.sourceId().startsWith("LAW:" + housing.lawId())),
                "적재한 법령의 source_id가 검색되지 않음: " + hits.stream().map(Retrieved::sourceId).toList());
        System.out.printf("[live] 적재→검색 왕복 OK — top: %s%n", hits.get(0).sourceId());
    }

    private static RawLaw loadPending(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = new ObjectMapper().readValue(body, LinkedHashMap.class);
            return new RawLaw(null, null, null, "시행예정", null, null, null, root);
        } catch (Exception e) {
            throw new RuntimeException("fixture 로드 실패: " + path, e);
        }
    }
}
