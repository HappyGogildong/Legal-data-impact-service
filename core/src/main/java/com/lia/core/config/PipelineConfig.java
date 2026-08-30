package com.lia.core.config;

import java.time.LocalDate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.lia.core.pipeline.connector.LawConnector;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.embed.Embedder;
import com.lia.core.pipeline.embed.EmbeddingProperties;
import com.lia.core.pipeline.embed.OpenAiEmbedder;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.QueryTranslator;
import com.lia.core.pipeline.plan.SpringAiQueryTranslator;
import com.lia.core.pipeline.resolve.ChunkStoreLawSearch;
import com.lia.core.pipeline.resolve.LawLookup;
import com.lia.core.pipeline.resolve.SourceAnalyzer;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.PgVectorChunkStore;

/**
 * 파이프라인 빈 조립 (D35: Python config.py 팩토리의 Spring 대응).
 * 커넥터·해소기는 설정에 비결합 — 여기서 프로퍼티를 주입해 조립한다.
 */
@Configuration
public class PipelineConfig {

    /** 해소 시 훑을 시행예정 범위 상한 — 지금부터 N년. */
    private static final int LOOKAHEAD_YEARS = 2;

    /** 국가법령정보 — MVP 분석 대상(eflaw) + diff 기준선(law). D42 */
    @Bean
    public LawConnector lawConnector(RestClient.Builder builder, LiaSourceProperties props,
                                     ObservationRegistry observations) {
        return new LawConnector(builder, props.law(), observations);
    }

    /**
     * 해소용 조회 포트. 지금은 커넥터를 감싸지만, Law Store 적재가 끝나면
     * 저장소 구현으로 교체한다 — 아키텍처 v0.8 §3.2 에서 해소는 *적재분*을 읽는다.
     * 교체 시 {@link SourceAnalyzer} 는 손대지 않는다.
     */
    @Bean
    public LawLookup lawLookup(LawConnector connector) {
        return (query, limit) -> {
            LocalDate from = LocalDate.now().plusDays(1);
            return connector.searchPending(query, from, from.plusYears(LOOKAHEAD_YEARS), limit);
        };
    }

    @Bean
    public Normalizer normalizer(ObservationRegistry observations) {
        return new Normalizer(observations);
    }

    /** 변경 조문 ↔ 시행중본 대조 (신설·삭제 확정 + diffVsCurrent). D42 */
    @Bean
    public DiffBuilder diffBuilder(ObservationRegistry observations) {
        return new DiffBuilder(observations);
    }

    /**
     * 적재·검색 공유 임베딩(D32). Spring AI {@link EmbeddingModel}(OpenAI 자동설정) 위임.
     * 벤더 확정 후 같은 포트에 구현체만 교체(D33). 계측은 Spring AI 내장(gen_ai.*)에 위임.
     */
    @Bean
    public Embedder embedder(EmbeddingModel embeddingModel, EmbeddingProperties props) {
        return new OpenAiEmbedder(embeddingModel, props);
    }

    /** 벡터 chunks 저장(D54) — 자동설정된 PgVectorStore를 래핑. add/search 시 내부 임베딩. */
    @Bean
    public ChunkStore chunkStore(VectorStore vectorStore) {
        return new PgVectorChunkStore(vectorStore);
    }

    /** 시행예정 코퍼스 색인(D55) — 변경조문·요약 청킹 → ChunkStore. 오프라인 배치. */
    @Bean
    public RAGIndexer ragIndexer(ChunkStore chunkStore) {
        return new RAGIndexer(chunkStore);
    }

    /** 해소기 — 정확매칭(LawLookup) + pending ns 의미검색(ChunkStore) 폴백. 임계 88/60(D23). */
    @Bean
    public SourceAnalyzer sourceAnalyzer(LawLookup lookup, ChunkStore chunkStore,
                                         ObservationRegistry observations) {
        var semanticSearch = new ChunkStoreLawSearch(chunkStore, 5);
        return new SourceAnalyzer(lookup, semanticSearch, 88.0, 60.0, observations);
    }

    /** 자연어 → AnalysisQueryDraft 번역(D46) — 유일한 LLM 자유도. Haiku 4.5. */
    @Bean
    public QueryTranslator queryTranslator(AnthropicChatModel anthropicChatModel) {
        return new SpringAiQueryTranslator(anthropicChatModel);
    }

    /** 자연어 → PlanResult. 번역 후 결정론(해소·게이팅·fail-closed, D46). */
    @Bean
    public QueryPlanner queryPlanner(QueryTranslator translator, SourceAnalyzer sourceAnalyzer) {
        return new QueryPlanner(translator, sourceAnalyzer);
    }
}
