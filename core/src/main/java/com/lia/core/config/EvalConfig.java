package com.lia.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.eval.OfflineEvalRunner;
import com.lia.core.eval.OfflineEvaluator;
import com.lia.core.eval.RagConfig;
import com.lia.core.pipeline.discover.LawDiscovery;
import com.lia.core.pipeline.plan.QueryPlanner;

/**
 * 오프라인 회귀 평가({@code eval} 프로파일, D83) — 서비스의 {@link QueryPlanner}·{@link LawDiscovery} 빈을 그대로 쓴다.
 * 웹 서버 없이 한 번 돌고 종료 코드를 남긴다. 유료 호출(Haiku·임베딩)이 있어 사람이 수동으로 실행한다.
 */
@Configuration
@Profile("eval")
public class EvalConfig {

    /** 색인된 법령 수 — 벡터 테이블은 PgVectorStore 소유(db/init.sql), 청크 메타의 법령ID로 센다. */
    private static final String COUNT_INDEXED_LAWS =
            "SELECT count(DISTINCT metadata->>'lawId') FROM vector_store";

    @Bean
    public OfflineEvaluator offlineEvaluator(QueryPlanner queryPlanner, LawDiscovery lawDiscovery) {
        return new OfflineEvaluator(queryPlanner, lawDiscovery);
    }

    @Bean
    public OfflineEvalRunner offlineEvalRunner(OfflineEvaluator evaluator, LawDiscovery lawDiscovery, JdbcClient jdbcClient,
                                               @Value("${lia.eval.gold}") String gold,
                                               @Value("${lia.eval.baseline}") String baseline,
                                               @Value("${lia.eval.tolerance}") double tolerance) {
        return new OfflineEvalRunner(evaluator::evaluate, RagConfig.of(lawDiscovery.settings()),
                () -> jdbcClient.sql(COUNT_INDEXED_LAWS).query(Long.class).single(),
                lawId -> true,
                gold, baseline, tolerance, System.out);
    }
}
