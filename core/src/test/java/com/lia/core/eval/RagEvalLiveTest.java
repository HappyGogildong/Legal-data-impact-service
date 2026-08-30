package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.domain.law.Law;
import com.lia.core.eval.GoldenSet.RetrievalCase;
import com.lia.core.pipeline.connector.LawConnector;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.ChunkStore;

/**
 * RAG <b>성능 평가</b>(자기검색 기준선) — 실 법령을 적재(실 OpenAI 임베딩)하고 요약 질의로 그 법령의
 * source_ids가 검색되는지 Recall@k·MRR로 실측. <b>비용 발생</b>(국가법령정보 + OpenAI). 기본 test 스킵.
 * 옵트인 {@code LIA_RAG_EVAL=1}(그때 {@code OPENAI_API_KEY}·{@code LAW_OC} 필요) + Docker. 사용자가 직접:
 * <pre>LIA_RAG_EVAL=1 ./gradlew test --tests "*RagEvalLiveTest"</pre>
 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "LIA_RAG_EVAL", matches = "(?i)(1|true|yes)")
class RagEvalLiveTest {

    private static final int LAWS = 10;   // 평가 표본(비용 제한)
    private static final int TOP_K = 5;

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "true");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired LawConnector connector;
    @Autowired Normalizer normalizer;
    @Autowired RAGIndexer ragIndexer;
    @Autowired ChunkStore chunkStore;

    @Test
    void 자기검색_Recall_기준선() {
        LocalDate from = LocalDate.now().plusDays(1);
        List<RawLaw> heads = connector.listPending(from, from.plusYears(1), LAWS);

        // 실 법령 적재(실 OpenAI 임베딩) + 골든셋용 Law 수집
        List<Law> indexed = new ArrayList<>();
        for (RawLaw head : heads) {
            Law law = normalizer.normalize(connector.fetchPending(head.mst(), head.effectiveDate()));
            ragIndexer.index(law);
            indexed.add(law);
        }

        List<RetrievalCase> gold = SelfRetrievalGold.fromLaws(indexed);
        assertFalse(gold.isEmpty(), "골든셋이 비었다 — 요약 있는 법령이 없음");

        EvalReport report = new RagEvalRunner(new ChunkStoreRetriever(chunkStore))
                .run(RagConfig.defaults().withTopK(TOP_K), gold);

        System.out.println("[eval] 자기검색 기준선 (n=" + gold.size() + ", topK=" + TOP_K + ")");
        report.metrics().forEach((k, v) -> System.out.printf("        %-10s %.3f%n", k, v));

        double recall5 = report.metrics().getOrDefault("recall@5", 0.0);
        assertTrue(recall5 >= 0.5,
                "자기검색 recall@5가 너무 낮다(" + recall5 + ") — 임베딩·색인·검색 점검 필요");
    }
}
