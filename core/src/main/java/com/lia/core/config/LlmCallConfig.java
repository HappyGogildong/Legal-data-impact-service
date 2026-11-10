package com.lia.core.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.observability.RecordWriter;
import com.lia.core.observability.llm.JdbcLlmCallRecorder;
import com.lia.core.observability.llm.LlmCall;
import com.lia.core.observability.llm.LlmCallRecorder;
import com.lia.core.observability.llm.LlmPurpose;
import com.lia.core.observability.llm.RecordingEmbeddingModel;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 유료 모델 호출 기록 배선(D83) — 스펙: docs/components/observability/LlmCallRecorder.md.
 * 웹은 비동기, 배치·평가는 동기({@code lia.llm-calls.async}). 임베딩은 자동설정 빈을 감싸 {@code PgVectorStore} 가
 * 쓰는 모든 경로가 기록되게 한다.
 */
@Configuration
public class LlmCallConfig {

    /** 호출 기록 쓰기 장치 — 닫을 때 비동기 대기열을 잠시 비운다. */
    @Bean(destroyMethod = "close")
    public RecordWriter<LlmCall> llmCallWriter(JdbcClient jdbcClient, MeterRegistry registry,
                                              @Value("${lia.llm-calls.async}") boolean async,
                                              @Value("${lia.llm-calls.queue-capacity}") int queueCapacity) {
        JdbcLlmCallRecorder store = new JdbcLlmCallRecorder(jdbcClient);
        return async ? RecordWriter.async("llm_call", store::record, queueCapacity, registry)
                : RecordWriter.sync("llm_call", store::record, registry);
    }

    /** 기록 포트 — 본 처리를 막거나 실패시키지 않는다. */
    @Bean
    public LlmCallRecorder llmCallRecorder(RecordWriter<LlmCall> llmCallWriter) {
        return llmCallWriter::write;
    }

    /**
     * 임베딩 모델을 기록 데코레이터로 감싼다. static — 후처리기는 다른 빈보다 먼저 만들어지므로 기록 포트는 감쌀 때가 아니라
     * 호출 때 꺼낸다(빈 생성 순서를 당기지 않게).
     */
    @Bean
    public static BeanPostProcessor recordingEmbeddingModelWrapper(ObjectProvider<LlmCallRecorder> recorder,
                                                                  Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof EmbeddingModel model) || bean instanceof RecordingEmbeddingModel) {
                    return bean;
                }
                LlmPurpose purpose = environment.getRequiredProperty("lia.llm-calls.embedding-purpose", LlmPurpose.class);
                return new RecordingEmbeddingModel(model, purpose, call -> recorder.getObject().record(call));
            }
        };
    }
}
