package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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

import com.lia.core.domain.law.Law;
import com.lia.core.store.LawStore;
import com.lia.core.testsupport.FakeLawFeed;
import com.lia.core.testsupport.TestDatabases;
import com.lia.core.testsupport.TestLaws;

/** 탐색 분류 규칙 — 목록 ⟷ 저장 상태 비교(실 Postgres + 가짜 원천). 오늘 = 2026-07-01 고정. */
@Testcontainers(disabledWithoutDocker = true)
class DiscovererIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDate TODAY = LocalDate.of(2026, 7, 1);
    static final LocalDate FUTURE = LocalDate.of(2027, 1, 1);

    static JdbcClient jdbc;
    static LawStore lawStore;
    static TaskQueue queue;
    FakeLawFeed feed;
    Discoverer discoverer;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        lawStore = new LawStore(jdbc, new ObjectMapper());
        queue = new TaskQueue(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM ingest_tasks").update();
        jdbc.sql("DELETE FROM law_versions").update();
        feed = new FakeLawFeed();
        discoverer = discovererOn(TODAY);
    }

    /** 그날을 오늘로 보는 탐색기(조회 범위 10년). */
    private Discoverer discovererOn(LocalDate day) {
        return new Discoverer(feed, lawStore, queue, Clock.fixed(day.atStartOfDay(SEOUL).toInstant(), SEOUL), 10);
    }

    private List<String> tasks() {
        return jdbc.sql("SELECT kind || ':' || law_id || '@' || effective_date FROM ingest_tasks ORDER BY task_id")
                .query(String.class).list();
    }

    @Test
    @DisplayName("신규 — 저장 안 된 법령 → SYNC")
    void 신규() {
        feed.head("A", "M1", FUTURE);

        DiscoveryResult r = discoverer.discover(false, false);

        assertEquals(List.of("SYNC:A@2027-01-01"), tasks());
        assertEquals(1, r.listed());
        assertTrue(r.listComplete());
        assertEquals(1, r.enqueued().get(TaskKind.SYNC));
    }

    @Test
    @DisplayName("MST 변경 — 저장된 mst 와 다르면 SYNC, 같으면 아무것도 안 함(fetch 생략)")
    void MST비교() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));
        lawStore.upsert(TestLaws.law("B", "M2", Law.Status.시행예정, FUTURE));
        feed.head("A", "M1", FUTURE).head("B", "M2-정정", FUTURE);

        discoverer.discover(false, false);

        assertEquals(List.of("SYNC:B@2027-01-01"), tasks());
    }

    @Test
    @DisplayName("동일하게 다시 보이면 missing_since 해제")
    void 다시보임_해제() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));
        lawStore.markMissing("A", FUTURE, TODAY.minusDays(1));
        feed.head("A", "M1", FUTURE);

        discoverer.discover(false, false);

        assertNull(lawStore.findPendingVersions("A").get(0).missingSince());
    }

    @Test
    @DisplayName("시행일 도래 — 시행일 ≤ 오늘인 시행예정본 → TRANSITION(목록 실패여도)")
    void 시행도래() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, TODAY));
        feed.failList(new IllegalStateException("목록 API 장애"));

        DiscoveryResult r = discoverer.discover(false, false);

        assertEquals(List.of("TRANSITION:A@2026-07-01"), tasks());
        assertFalse(r.listComplete());
        assertEquals(0, r.listed());
    }

    @Test
    @DisplayName("사라짐 — 첫 미확인은 missing_since 기록만, 다음 날 두 번째 미확인에 PURGE")
    void 사라짐_두번() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));

        DiscoveryResult first = discoverer.discover(false, false);
        assertTrue(tasks().isEmpty(), "첫 미확인은 정리하지 않는다");
        assertEquals(1, first.markedMissing());
        assertEquals(TODAY, lawStore.findPendingVersions("A").get(0).missingSince());

        discovererOn(TODAY.plusDays(1)).discover(false, false);
        assertEquals(List.of("PURGE:A@2027-01-01"), tasks());
    }

    @Test
    @DisplayName("같은 날 다시 돌려도 PURGE 하지 않는다 — 같은 API 상태라 새 증거가 아니다")
    void 같은날_재실행() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));

        discoverer.discover(false, false);
        DiscoveryResult second = discoverer.discover(false, false);

        assertTrue(tasks().isEmpty());
        assertEquals(0, second.markedMissing());
        assertEquals(TODAY, lawStore.findPendingVersions("A").get(0).missingSince(), "기록 날짜 유지");
    }

    @Test
    @DisplayName("조회 범위(오늘+10년) 밖의 시행예정본은 사라짐 판정을 하지 않는다")
    void 범위밖_판정안함() {
        lawStore.upsert(TestLaws.law("FAR", "M9", Law.Status.시행예정, TODAY.plusYears(10).plusDays(1)));

        DiscoveryResult r = discoverer.discover(false, false);

        assertEquals(0, r.markedMissing());
        assertNull(lawStore.findPendingVersions("FAR").get(0).missingSince());
    }

    @Test
    @DisplayName("미완주 목록이라도 보인 법령의 missing_since 는 해제 — 보였다는 건 확실한 증거")
    void 미완주_보이면해제() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));
        lawStore.markMissing("A", FUTURE, TODAY.minusDays(1));
        feed.head("A", "M1", FUTURE).listComplete(false);

        discoverer.discover(false, false);

        assertNull(lawStore.findPendingVersions("A").get(0).missingSince());
    }

    @Test
    @DisplayName("같은 (법령, 시행일)이 목록에 둘 — 첫 행만 쓰고 건수를 남긴다(키 가정 위반 신호)")
    void 중복키() {
        feed.head("A", "M1", FUTURE).head("A", "M2", FUTURE);

        DiscoveryResult r = discoverer.discover(false, false);

        assertEquals(1, r.duplicateKeys());
        assertEquals(List.of("SYNC:A@2027-01-01"), tasks());
    }

    @Test
    @DisplayName("잘못된 목록 행(lawId·시행일·MST 누락) — 건너뛰고 나머지·시행 전환은 진행, 목록은 미완주로 취급")
    void 잘못된행() {
        lawStore.upsert(TestLaws.law("DUE", "M0", Law.Status.시행예정, TODAY));     // 시행 도래
        lawStore.upsert(TestLaws.law("GONE", "M9", Law.Status.시행예정, FUTURE));   // 목록에 없음
        feed.head("A", "M1", FUTURE).head(null, "MX", FUTURE).head("C", null, FUTURE).head("D", "MD", null);

        DiscoveryResult r = discoverer.discover(false, false);

        assertEquals(List.of("SYNC:A@2027-01-01", "TRANSITION:DUE@2026-07-01"), tasks());
        assertFalse(r.listComplete(), "그 행이 어느 법령인지 몰라 사라짐 판정을 할 수 없다");
        assertEquals(3, r.malformedRows());
        assertEquals(0, r.markedMissing());
        assertNull(lawStore.findPendingVersions("GONE").get(0).missingSince());
    }

    @Test
    @DisplayName("철회된 버전이 목록에 다시 보이면 신규로 분류 → SYNC")
    void 철회본_재등장() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.철회, FUTURE));
        feed.head("A", "M1", FUTURE);

        discoverer.discover(false, false);

        assertEquals(List.of("SYNC:A@2027-01-01"), tasks());
    }

    @Test
    @DisplayName("목록 미완주면 사라짐 판정을 하지 않는다")
    void 미완주_판정안함() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));
        lawStore.markMissing("A", FUTURE, TODAY.minusDays(1));
        feed.listComplete(false);

        DiscoveryResult r = discoverer.discover(false, false);

        assertTrue(tasks().isEmpty(), "미완주 목록으로 PURGE 금지");
        assertEquals(0, r.markedMissing());
        assertFalse(r.listComplete());
    }

    @Test
    @DisplayName("--full — MST 가 같아도 목록 전부 SYNC")
    void 전체재검증() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, FUTURE));
        feed.head("A", "M1", FUTURE);

        discoverer.discover(true, false);

        assertEquals(List.of("SYNC:A@2027-01-01"), tasks());
    }

    @Test
    @DisplayName("--dry-run — 계획 건수만 내고 등록·missing 기록을 하지 않는다")
    void 시험실행() {
        lawStore.upsert(TestLaws.law("GONE", "M0", Law.Status.시행예정, FUTURE));
        feed.head("A", "M1", FUTURE);

        DiscoveryResult r = discoverer.discover(false, true);

        assertTrue(tasks().isEmpty());
        assertEquals(1, r.planned().get(TaskKind.SYNC));
        assertTrue(r.enqueued().isEmpty());
        assertNull(lawStore.findPendingVersions("GONE").get(0).missingSince(), "dry-run 은 쓰지 않는다");
    }
}
