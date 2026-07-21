package com.lia.core.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.lia.core.pipeline.connector.AssemblyBillsConnector;
import com.lia.core.pipeline.connector.SourceConnector;
import com.lia.core.pipeline.resolve.SourceAnalyzer;

/**
 * 파이프라인 빈 조립 (D35: Python config.py 팩토리의 Spring 대응).
 * 커넥터는 설정에 비결합 — 여기서 프로퍼티를 주입해 조립한다.
 */
@Configuration
public class PipelineConfig {

    @Bean
    public AssemblyBillsConnector assemblyBillsConnector(
            RestClient.Builder builder, LiaSourceProperties props) {
        return new AssemblyBillsConnector(builder, props.assembly());
    }

    @Bean
    public SourceAnalyzer sourceAnalyzer(List<SourceConnector> connectors) {
        // 의미검색(semanticSearch)은 Embedder/VectorStore 구현 후 주입 (v0.6 §3.4)
        return new SourceAnalyzer(connectors);
    }
}
