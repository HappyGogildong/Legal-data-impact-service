package com.lia.core.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.ObjectMapper;

import com.lia.core.pipeline.batch.Discoverer;
import com.lia.core.pipeline.batch.IndexHandler;
import com.lia.core.pipeline.batch.IngestJob;
import com.lia.core.pipeline.batch.IngestJobRunner;
import com.lia.core.pipeline.batch.IngestRunStore;
import com.lia.core.pipeline.batch.MinIntervalLimiter;
import com.lia.core.pipeline.batch.PurgeHandler;
import com.lia.core.pipeline.batch.RateLimitedLawFeed;
import com.lia.core.pipeline.batch.SummarizeHandler;
import com.lia.core.pipeline.batch.SyncHandler;
import com.lia.core.pipeline.batch.TaskHandler;
import com.lia.core.pipeline.batch.TaskQueue;
import com.lia.core.pipeline.batch.TransitionHandler;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.SpringAiReasoner;
import com.lia.core.pipeline.connector.LawConnector;
import com.lia.core.pipeline.connector.PendingLawFeed;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;
import com.lia.core.store.LawSummaries;
import com.lia.core.store.LawSummaryStore;
import com.lia.core.store.NotificationStore;

/**
 * 적재 배치 배선(D62) — {@code batch} 프로파일에서만. 웹 컨텍스트에는 배치 빈이 없다.
 * 설정값은 {@code application-batch.yml}의 {@code lia.batch.*}.
 */
@Configuration
@Profile("batch")
public class BatchConfig {

    @Bean
    public Clock batchClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public TaskQueue taskQueue(JdbcClient jdbc) {
        return new TaskQueue(jdbc);
    }

    @Bean
    public IngestRunStore ingestRunStore(JdbcClient jdbc, ObjectMapper json) {
        return new IngestRunStore(jdbc, json);
    }

    /**
     * 국가법령정보 호출 간격 제한을 건 원천. {@code LawConnector} 자신도 {@code PendingLawFeed}라서
     * 배치의 {@code PendingLawFeed} 주입은 이 빈을 쓰도록 {@code @Primary}로 모호성을 푼다.
     */
    @Bean
    @Primary
    public PendingLawFeed pendingLawFeed(LawConnector connector,
                                         @Value("${lia.batch.law-api-interval}") Duration interval) {
        return new RateLimitedLawFeed(connector, new MinIntervalLimiter(interval));
    }

    @Bean
    public Discoverer discoverer(PendingLawFeed feed, LawStore lawStore, TaskQueue queue, Clock batchClock,
                                 @Value("${lia.batch.lookahead-years}") int lookaheadYears) {
        return new Discoverer(feed, lawStore, queue, batchClock, lookaheadYears);
    }

    @Bean
    public IngestJob ingestJob(TaskQueue queue, IngestRunStore runs, Discoverer discoverer, LawStore lawStore,
                               PendingLawFeed feed, IngestService ingest, ChunkStore chunkStore, RAGIndexer indexer,
                               Clock batchClock, ObservationRegistry observations,
                               AnalysisEngine analysisEngine, LawSummaryStore lawSummaryStore,
                               LawSummaries lawSummaries, NotificationStore notificationStore,
                               PlatformTransactionManager transactionManager,
                               @Value("${lia.batch.workers}") int workers,
                               @Value("${lia.batch.index.enabled}") boolean indexEnabled,
                               @Value("${lia.batch.summary.enabled}") boolean summaryEnabled,
                               @Value("${lia.batch.embedding-interval}") Duration embeddingInterval,
                               @Value("${lia.batch.summary-interval}") Duration summaryInterval,
                               @Value("${lia.batch.idle-poll}") Duration idlePoll,
                               @Value("${lia.batch.done-retention}") Duration doneRetention) {
        // 같은 PlatformTransactionManager — LawStore·TaskQueue·ChunkStore 가 한 트랜잭션에 참여한다
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        List<TaskHandler> handlers = List.of(
                new SyncHandler(feed, ingest, lawStore, queue, transactions, batchClock),
                new IndexHandler(lawStore, indexer, new MinIntervalLimiter(embeddingInterval)),
                new SummarizeHandler(lawStore, analysisEngine, lawSummaryStore, lawSummaries,
                        new MinIntervalLimiter(summaryInterval),
                        SpringAiReasoner.MODEL),
                new TransitionHandler(lawStore, chunkStore, queue, transactions),
                new PurgeHandler(lawStore, chunkStore, transactions));
        return new IngestJob(queue, runs, discoverer, lawStore, lawSummaries, notificationStore, handlers, workers, indexEnabled,
                summaryEnabled, idlePoll,
                doneRetention, batchClock, observations);
    }

    @Bean
    public IngestJobRunner ingestJobRunner(IngestJob job, DataSource dataSource) {
        return new IngestJobRunner(job, dataSource);
    }
}
