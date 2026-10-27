package com.lia.core.store;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;

/**
 * 쉬운 요약 저장소(D67) — {@code law_summaries}. 배치({@code SummarizeHandler})가 쓴다. 읽기는 이 저장소가 아니라
 * {@link LawSummaries}(인용이 지금 근거 안인지까지 확인하는 {@code GroundedSummaries})로 — 이쪽 {@link #findStored}는 날것의 조회다.
 * 저장본 유효 = 정본 revision·프롬프트 버전 일치. 빈은 {@code PipelineConfig}(프롬프트 버전 주입).
 */
public class LawSummaryStore {

    private static final Logger log = LoggerFactory.getLogger(LawSummaryStore.class);

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final String promptVersion;

    public LawSummaryStore(JdbcClient jdbc, ObjectMapper json, String promptVersion) {
        this.jdbc = jdbc;
        this.json = json;
        this.promptVersion = promptVersion;
    }

    /** 그 버전의 요약을 저장(있으면 교체) — 지금 정본 revision·프롬프트 버전과 함께. */
    public void save(Law law, ImpactResult result, String model) {
        jdbc.sql("""
                INSERT INTO law_summaries (law_id, effective_date, revision, prompt_version, model, result, created_at)
                VALUES (:lawId, :efYd, :revision, :promptVersion, :model, CAST(:result AS jsonb), now())
                ON CONFLICT (law_id, effective_date) DO UPDATE SET
                  revision = EXCLUDED.revision, prompt_version = EXCLUDED.prompt_version,
                  model = EXCLUDED.model, result = EXCLUDED.result, created_at = EXCLUDED.created_at
                """)
                .param("lawId", law.lawId())
                .param("efYd", law.effectiveDate())
                .param("revision", law.revision())
                .param("promptVersion", promptVersion)
                .param("model", model)
                .param("result", json.writeValueAsString(result))
                .update();
    }

    /** 그 버전의 저장본 — 정본 revision·프롬프트 버전이 지금과 같을 때만(인용의 근거 확인 전 날것). 없거나 낡으면 empty. */
    public Optional<ImpactResult> findStored(Law law) {
        return jdbc.sql("""
                SELECT result FROM law_summaries
                WHERE law_id = :lawId AND effective_date = :efYd
                  AND revision = :revision AND prompt_version = :promptVersion
                """)
                .param("lawId", law.lawId())
                .param("efYd", law.effectiveDate())
                .param("revision", law.revision())
                .param("promptVersion", promptVersion)
                .query(String.class)
                .optional()
                .flatMap(text -> read(law, text));
    }

    /**
     * 읽을 수 없는 저장본(결과 스키마 변경 등)은 낡은 것과 같이 취급한다 — 요청을 실패시키지 않는다.
     * 다시 만들려면 {@code summarize} 명령을 돌려야 한다(적재는 대기 내용이 바뀔 때만 요약을 다시 등록한다).
     */
    private Optional<ImpactResult> read(Law law, String text) {
        try {
            return Optional.of(json.readValue(text, ImpactResult.class));
        } catch (JacksonException e) {
            log.warn("[summary] 저장된 요약을 읽을 수 없음 — 낡은 것으로 취급: {}@{}", law.lawId(), law.effectiveDate());
            return Optional.empty();
        }
    }
}
