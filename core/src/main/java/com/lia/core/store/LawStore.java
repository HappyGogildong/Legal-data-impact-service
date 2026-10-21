package com.lia.core.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    // --- 배치 계약(IngestJob, D62) -------------------------------------------

    private static final String PENDING_VERSIONS = """
            SELECT law_id, effective_date, payload->>'mst' AS mst, missing_since
            FROM law_versions WHERE status = '시행예정'
            """;

    /**
     * 증분 비교용 저장 revision — payload 역직렬화 없이. 행이 없거나 <b>상태가 다르면</b> empty
     * (철회된 행에 같은 내용이 다시 오면 "다름"으로 보여 시행예정으로 복원되게, D62).
     */
    public Optional<String> revisionOf(String lawId, LocalDate effectiveDate, Law.Status status) {
        return jdbc.sql("""
                SELECT revision FROM law_versions
                WHERE law_id = :lawId AND effective_date = :efYd AND status = :status
                """)
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .param("status", status.name())
                .query(String.class)
                .optional();
    }

    /** 시행예정 전체(탐색이 목록과 비교). */
    public List<PendingVersion> listPendingVersions() {
        return jdbc.sql(PENDING_VERSIONS + " ORDER BY law_id, effective_date")
                .query((rs, rowNum) -> toPendingVersion(rs))
                .list();
    }

    /** 한 법령의 시행예정본들(시행 전환 시 형제 재수집). */
    public List<PendingVersion> findPendingVersions(String lawId) {
        return jdbc.sql(PENDING_VERSIONS + " AND law_id = :lawId ORDER BY effective_date")
                .param("lawId", lawId)
                .query((rs, rowNum) -> toPendingVersion(rs))
                .list();
    }

    /** 상태 변경 — 컬럼과 payload 를 함께 바꾼다({@link #find}는 payload 를 역직렬화하므로). */
    public void updateStatus(String lawId, LocalDate effectiveDate, Law.Status status) {
        jdbc.sql("""
                UPDATE law_versions
                SET status = :status, payload = jsonb_set(payload, '{status}', to_jsonb(CAST(:status AS text)))
                WHERE law_id = :lawId AND effective_date = :efYd
                """)
                .param("status", status.name())
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .update();
    }

    /** 같은 법령에서 기준일보다 오래된 {@code 시행중} 행을 {@code 과거}로. 바뀐 행 수. */
    public int supersedeOlderInForce(String lawId, LocalDate effectiveDate) {
        return jdbc.sql("""
                UPDATE law_versions
                SET status = '과거', payload = jsonb_set(payload, '{status}', to_jsonb('과거'::text))
                WHERE law_id = :lawId AND status = '시행중' AND effective_date < :efYd
                """)
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .update();
    }

    /** 목록에서 처음 안 보인 날 기록(첫 번째 미확인). */
    public void markMissing(String lawId, LocalDate effectiveDate, LocalDate since) {
        jdbc.sql("UPDATE law_versions SET missing_since = :since WHERE law_id = :lawId AND effective_date = :efYd")
                .param("since", since)
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .update();
    }

    /** 다시 보였거나 다시 수집됨 — 사라짐 판정 해제. */
    public void clearMissing(String lawId, LocalDate effectiveDate) {
        jdbc.sql("UPDATE law_versions SET missing_since = NULL WHERE law_id = :lawId AND effective_date = :efYd")
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .update();
    }

    private static PendingVersion toPendingVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PendingVersion(rs.getString("law_id"), rs.getObject("effective_date", LocalDate.class),
                rs.getString("mst"), rs.getObject("missing_since", LocalDate.class));
    }

    private Law deserialize(String payload) {
        return json.readValue(payload, Law.class);
    }
}
