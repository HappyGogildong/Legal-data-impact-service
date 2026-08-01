package com.lia.core.config;

import java.time.LocalDate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.lia.core.pipeline.connector.LawConnector;
import com.lia.core.pipeline.resolve.LawLookup;
import com.lia.core.pipeline.resolve.SourceAnalyzer;

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
    public LawConnector lawConnector(RestClient.Builder builder, LiaSourceProperties props) {
        return new LawConnector(builder, props.law());
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
    public SourceAnalyzer sourceAnalyzer(LawLookup lookup) {
        // 의미검색(semanticSearch)은 Embedder/VectorStore 구현 후 주입 (v0.8 §4.5)
        return new SourceAnalyzer(lookup);
    }
}
