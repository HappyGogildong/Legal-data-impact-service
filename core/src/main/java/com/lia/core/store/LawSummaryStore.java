package com.lia.core.store;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;

/**
 * 쉬운 요약 저장소(D67) — {@code law_summaries}. 배치({@code SummarizeHandler})가 쓰고, 온라인은 {@link LawSummaries}로
 * 읽는다. 유효 = 정본 revision·프롬프트 버전 일치. 빈은 {@code PipelineConfig}(프롬프트 버전 주입).
 */
public class LawSummaryStore implements LawSummaries {

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

    @Override
    public Optional<ImpactResult> findCurrent(Law law) {
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
                .flatMap(this::read);
    }

    /** 읽을 수 없는 저장본(결과 스키마 변경 등)은 낡은 것과 같이 취급한다 — 요청을 실패시키지 않고 다음 적재가 다시 만든다. */
    private Optional<ImpactResult> read(String text) {
        try {
            return Optional.of(json.readValue(text, ImpactResult.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
