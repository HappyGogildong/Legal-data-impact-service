package com.lia.core.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Law;

/**
 * 법령 정본 저장소 — {@code law_versions}(JSONB) upsert·조회(D54).
 *
 * <p>{@code Law} 애그리거트 전체를 {@code payload jsonb} 에 통째 저장하고(정본 SSOT),
 * 조회 시 역직렬화한다. 벡터 {@code chunks} 는 RAGIndexer 단계에서 별도(PgVectorStore).
 *
 * <p>near-term 범위: {@code upsert}·{@code find}·{@code findBaseline}. LawFacts·ImpactResult
 * 캐시와 벡터는 후속. 스키마: {@code db/migration/V1__law_versions.sql}. 설계: docs/components/LawStore.md
 */
@Repository
public class LawStore implements LawSource {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public LawStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 정본 upsert — {@code (lawId, effectiveDate)} 단위 멱등(재적재 안전). */
    public void upsert(Law law) {
        jdbc.sql("""
                INSERT INTO law_versions
                  (law_id, effective_date, revision, status, title, baseline_law_id, payload, last_seen)
                VALUES
                  (:lawId, :efYd, :revision, :status, :title, :baseline, CAST(:payload AS jsonb), :lastSeen)
                ON CONFLICT (law_id, effective_date) DO UPDATE SET
                  revision = EXCLUDED.revision, status = EXCLUDED.status, title = EXCLUDED.title,
                  baseline_law_id = EXCLUDED.baseline_law_id, payload = EXCLUDED.payload,
                  last_seen = EXCLUDED.last_seen
                """)
                .param("lawId", law.lawId())
                .param("efYd", law.effectiveDate())
                .param("revision", law.revision())
                .param("status", law.status().name())
                .param("title", law.title())
                .param("baseline", law.baselineLawId())
                .param("payload", json.writeValueAsString(law))
                .param("lastSeen", OffsetDateTime.ofInstant(law.lastSeen(), ZoneOffset.UTC))
                .update();
    }

    /** 특정 버전 정본. */
    public Optional<Law> find(String lawId, LocalDate effectiveDate) {
        return jdbc.sql("SELECT payload FROM law_versions WHERE law_id = :lawId AND effective_date = :efYd")
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .query(String.class)
                .optional()
                .map(this::deserialize);
    }

    /**
     * 같은 {@code lawId} 의 시행중본(diff 기준선). <b>없으면 empty</b>(제정 = 현행본 없음,
     * docs/reference/law-domain-basics.md §3). 복수면 가장 최근 시행분.
     */
    public Optional<Law> findBaseline(String lawId) {
        return jdbc.sql("""
                SELECT payload FROM law_versions
                WHERE law_id = :lawId AND status = '시행중'
                ORDER BY effective_date DESC
                LIMIT 1
                """)
                .param("lawId", lawId)
                .query(String.class)
                .optional()
                .map(this::deserialize);
    }

    private Law deserialize(String payload) {
        return json.readValue(payload, Law.class);
    }
}
