package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.testsupport.TestDatabases;
import com.lia.core.testsupport.TestLaws;

/** 쉬운 요약 저장소 — 저장·덮어쓰기·revision/프롬프트 버전 낡음. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class LawSummaryStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate EF = LocalDate.of(2027, 1, 1);

    static JdbcClient jdbc;
    static LawSummaryStore summaries;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        summaries = new LawSummaryStore(jdbc, new ObjectMapper(), "p1");
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM law_summaries").update();
    }

    private static ImpactResult summary(String text) {
        return new ImpactResult("LAW:A@2027-01-01", "SUMMARY", text,
                List.of(new ImpactResult.Claim("주장", List.of("LAW:A@2027-01-01:art:1"), 0.9)),
                List.of(), List.of(), null, List.of("불확실"), "참고용", null);
    }

    @Test
    @DisplayName("저장 → 같은 revision·프롬프트 버전이면 그대로 돌려준다, 다시 저장하면 덮어쓴다")
    void 저장과_덮어쓰기() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);

        summaries.save(law, summary("첫 요약"), "claude-opus-4-8");
        assertEquals("첫 요약", summaries.findStored(law).orElseThrow().summary());

        summaries.save(law, summary("새 요약"), "claude-opus-4-8");
        ImpactResult found = summaries.findStored(law).orElseThrow();
        assertEquals("새 요약", found.summary());
        assertEquals(List.of("LAW:A@2027-01-01:art:1"), found.claims().get(0).citations());
        assertEquals(List.of("불확실"), found.uncertainties());
        assertEquals(1, jdbc.sql("SELECT count(*) FROM law_summaries").query(Integer.class).single());
    }

    @Test
    @DisplayName("중첩 필드(시행 정보·영향·대응·메타)까지 그대로 왕복")
    void 전체_필드_왕복() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);
        ImpactResult full = new ImpactResult("LAW:A@2027-01-01", "SUMMARY", "요약",
                List.of(new ImpactResult.Claim("주장", List.of("LAW:A@2027-01-01:art:1"), 0.9)),
                List.of(new ImpactResult.Impact("비용", "증가", "수수료가 오른다", List.of("LAW:A@2027-01-01:art:1"))),
                List.of(new ImpactResult.Action("신고", "2027-01-01", List.of("LAW:A@2027-01-01:addenda:제1조"))),
                new ImpactResult.EffectiveInfo("시행예정", EF, "유예"),
                List.of(), "참고용", new ImpactResult.Meta("claude-opus-4-8", "p1", "A"));

        summaries.save(law, full, "claude-opus-4-8");

        assertEquals(full, summaries.findStored(law).orElseThrow());
    }

    @Test
    @DisplayName("저장된 JSON 을 읽을 수 없으면(스키마 변경 등) 낡은 것과 같이 empty — 요청을 실패시키지 않는다")
    void 읽을_수_없는_행() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);
        summaries.save(law, summary("요약"), "claude-opus-4-8");
        jdbc.sql("UPDATE law_summaries SET result = CAST('{\"claims\": \"문자열\"}' AS jsonb)").update();

        assertTrue(summaries.findStored(law).isEmpty());
    }

    @Test
    @DisplayName("정본 revision 이 바뀌거나 프롬프트 버전이 다르면 낡음 — empty")
    void 낡음() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);
        summaries.save(law, summary("요약"), "claude-opus-4-8");

        Law changed = TestLaws.law("A", "M2", Law.Status.시행예정, EF);   // revision rev-M2
        assertTrue(summaries.findStored(changed).isEmpty());

        LawSummaryStore newerPrompt = new LawSummaryStore(jdbc, new ObjectMapper(), "p2");
        assertTrue(newerPrompt.findStored(law).isEmpty());
        assertTrue(summaries.findStored(TestLaws.law("B", "M1", Law.Status.시행예정, EF)).isEmpty());
    }

    @Test
    @DisplayName("한 버전의 요약과 변경점은 따로 저장된다")
    void 요약과_변경점_분리() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);
        Law baseline = TestLaws.law("A", "B1", Law.Status.시행중, LocalDate.of(2026, 1, 1));

        summaries.save(law, summary("요약"), "claude-opus-4-8");
        summaries.saveDiff(law, baseline, summary("변경점"), "claude-opus-4-8");

        assertEquals("요약", summaries.findStored(law).orElseThrow().summary());
        assertEquals("변경점", summaries.findStoredDiff(law, baseline).orElseThrow().summary());
        assertEquals(2, jdbc.sql("SELECT count(*) FROM law_summaries").query(Integer.class).single());

        summaries.saveDiff(law, baseline, summary("새 변경점"), "claude-opus-4-8");
        assertEquals("새 변경점", summaries.findStoredDiff(law, baseline).orElseThrow().summary());
        assertEquals("요약", summaries.findStored(law).orElseThrow().summary(), "요약은 그대로");
        assertEquals(2, jdbc.sql("SELECT count(*) FROM law_summaries").query(Integer.class).single());
    }

    @Test
    @DisplayName("변경점은 기준선·정본 revision·프롬프트 버전이 하나라도 다르면 낡은 것이다")
    void 변경점_낡음() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);
        Law baseline = TestLaws.law("A", "B1", Law.Status.시행중, LocalDate.of(2026, 1, 1));
        summaries.saveDiff(law, baseline, summary("변경점"), "claude-opus-4-8");

        Law newBaseline = TestLaws.law("A", "B2", Law.Status.시행중, LocalDate.of(2026, 1, 1));
        assertTrue(summaries.findStoredDiff(law, newBaseline).isEmpty(), "기준선 revision 이 다르다");
        assertTrue(summaries.findStoredDiff(law, null).isEmpty(), "기준선이 사라졌다");
        assertTrue(summaries.findStoredDiff(TestLaws.law("A", "M2", Law.Status.시행예정, EF), baseline).isEmpty());
        assertTrue(new LawSummaryStore(jdbc, new ObjectMapper(), "p2").findStoredDiff(law, baseline).isEmpty());
    }

    @Test
    @DisplayName("제정은 기준선 없이 변경점을 저장·조회한다")
    void 제정_변경점() {
        Law law = TestLaws.law("A", "M1", Law.Status.시행예정, EF);

        summaries.saveDiff(law, null, summary("전부 신설"), "claude-opus-4-8");

        assertEquals("전부 신설", summaries.findStoredDiff(law, null).orElseThrow().summary());
        assertTrue(summaries.findStored(law).isEmpty(), "변경점이 요약으로 읽히지 않는다");
    }

    @Test
    @DisplayName("V9 — ingest_tasks.kind 가 SUMMARIZE 를 받는다")
    void 작업_종류() {
        jdbc.sql("INSERT INTO ingest_tasks (kind, law_id, effective_date, status, created_at, updated_at) VALUES ('SUMMARIZE', 'A', :e, 'READY', now(), now())")
                .param("e", EF).update();
        assertEquals(1, jdbc.sql("SELECT count(*) FROM ingest_tasks WHERE kind = 'SUMMARIZE'").query(Integer.class).single());
    }
}
