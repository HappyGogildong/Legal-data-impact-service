package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.store.AnalysisHistoryStore.HistoryEntry;
import com.lia.core.store.AnalysisHistoryStore.HistoryRow;
import com.lia.core.store.AnalysisHistoryStore.NewEntry;
import com.lia.core.store.AnalysisHistoryStore.SaveResult;
import com.lia.core.store.AnalysisHistoryStore.SaveStatus;
import com.lia.core.testsupport.TestDatabases;

/** 질의·분석 이력(D73) — 멱등·한도·만료·cascade·남의 기록·갱신 판정·최신 시행예정본. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class AnalysisHistoryStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate EFFECTIVE = LocalDate.of(2026, 11, 8);
    static final String SNAPSHOT = "{\"body\":{\"resolution\":\"RESOLVED\"},\"answers\":[]}";

    static JdbcClient jdbc;
    static TransactionTemplate transactions;
    UUID userId;

    @BeforeAll
    static void setup() {
        DataSource dataSource = TestDatabases.migrate(PG);
        jdbc = JdbcClient.create(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM analysis_history").update();
        jdbc.sql("DELETE FROM law_versions").update();
        userId = account();
        version("001809", EFFECTIVE, "시행예정", "rev-1");
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (user_id, provider, provider_id, created_at) VALUES (:id, 'kakao', :sub, now())")
                .param("id", id).param("sub", id.toString()).update();
        return id;
    }

    private void version(String lawId, LocalDate effectiveDate, String status, String revision) {
        jdbc.sql("""
                INSERT INTO law_versions (law_id, effective_date, revision, status, title, payload, last_seen)
                VALUES (:lawId, :efYd, :revision, :status, '주택법', '{}'::jsonb, now())
                """)
                .param("lawId", lawId).param("efYd", effectiveDate)
                .param("revision", revision).param("status", status).update();
    }

    private static NewEntry entry(String analysisId) {
        return new NewEntry(analysisId, "주택법 바뀌면 전세 계약 어떻게 돼?", "001809", EFFECTIVE, "주택법", SNAPSHOT);
    }

    private AnalysisHistoryStore store(int max) {
        return new AnalysisHistoryStore(jdbc, transactions, max);
    }

    @Test
    void 저장은_같은_analysisId_면_기존_id_를_돌려준다() {
        AnalysisHistoryStore store = store(10);

        SaveResult first = store.save(userId, entry("a-1"));
        SaveResult again = store.save(userId, entry("a-1"));

        assertEquals(SaveStatus.SAVED, first.status());
        assertNotNull(first.id());
        assertEquals(SaveStatus.ALREADY, again.status());
        assertEquals(first.id(), again.id());
        assertEquals(1, store.list(userId).size());
    }

    @Test
    void 한도에_닿으면_새_저장은_거부하고_이미_저장한_것은_ALREADY() {
        AnalysisHistoryStore store = store(2);
        store.save(userId, entry("a-1"));
        store.save(userId, entry("a-2"));

        assertEquals(SaveStatus.LIMIT, store.save(userId, entry("a-3")).status());
        assertEquals(SaveStatus.ALREADY, store.save(userId, entry("a-1")).status());
    }

    @Test
    void 그_법령_버전_행이_없으면_LAW_NOT_FOUND() {
        NewEntry missing = new NewEntry("a-1", "q", "999999", EFFECTIVE, "없는 법", SNAPSHOT);
        assertEquals(SaveStatus.LAW_NOT_FOUND, store(10).save(userId, missing).status());
    }

    @Test
    void 계정이_없으면_ACCOUNT_NOT_FOUND() {
        assertEquals(SaveStatus.ACCOUNT_NOT_FOUND, store(10).save(UUID.randomUUID(), entry("a-1")).status());
    }

    @Test
    void 계정을_지우면_이력도_지워진다() {
        store(10).save(userId, entry("a-1"));
        jdbc.sql("DELETE FROM accounts WHERE user_id = :id").param("id", userId).update();
        assertEquals(0, jdbc.sql("SELECT count(*) FROM analysis_history").query(Integer.class).single());
    }

    @Test
    void 목록은_저장_최근_순이고_내_것만() {
        AnalysisHistoryStore store = store(10);
        store.save(userId, entry("a-1"));
        jdbc.sql("UPDATE analysis_history SET created_at = now() - interval '1 hour' WHERE analysis_id = 'a-1'").update();
        store.save(userId, entry("a-2"));
        store.save(account(), entry("a-3"));

        List<HistoryRow> rows = store.list(userId);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).savedAt().isAfter(rows.get(1).savedAt()));
        assertEquals("주택법", rows.get(0).lawTitle());
    }

    @Test
    void 상세는_스냅샷과_최신_시행예정본을_준다_남의_것은_없다() {
        AnalysisHistoryStore store = store(10);
        long id = store.save(userId, entry("a-1")).id();
        version("001809", LocalDate.of(2027, 2, 1), "시행예정", "rev-x");

        HistoryEntry found = store.find(userId, id).orElseThrow();
        assertTrue(found.snapshotJson().contains("RESOLVED"));
        assertEquals(EFFECTIVE, found.latestEffectiveDate(), "시행예정본이 여럿이면 가장 이른 것");
        assertTrue(store.find(account(), id).isEmpty());
    }

    @Test
    void 시행예정본이_없으면_latestEffectiveDate_는_null() {
        AnalysisHistoryStore store = store(10);
        long id = store.save(userId, entry("a-1")).id();
        jdbc.sql("UPDATE law_versions SET status = '시행중'").update();

        assertNull(store.find(userId, id).orElseThrow().latestEffectiveDate());
    }

    @Test
    void 갱신_판정_그대로면_false_revision_상태_행_변화면_true() {
        AnalysisHistoryStore store = store(10);
        long id = store.save(userId, entry("a-1")).id();
        assertFalse(store.find(userId, id).orElseThrow().row().lawUpdated());

        jdbc.sql("UPDATE law_versions SET revision = 'rev-2'").update();
        assertTrue(store.find(userId, id).orElseThrow().row().lawUpdated(), "내용 변경");

        jdbc.sql("UPDATE law_versions SET revision = 'rev-1', status = '시행중'").update();
        assertTrue(store.list(userId).getFirst().lawUpdated(), "시행됨");

        jdbc.sql("DELETE FROM law_versions").update();
        assertTrue(store.list(userId).getFirst().lawUpdated(), "행이 없어짐");
    }

    @Test
    void 삭제는_멱등이고_남의_것은_지우지_않는다() {
        AnalysisHistoryStore store = store(10);
        long id = store.save(userId, entry("a-1")).id();

        store.delete(account(), id);
        assertEquals(1, store.list(userId).size());

        store.delete(userId, id);
        store.delete(userId, id);
        assertTrue(store.list(userId).isEmpty());
    }

    @Test
    void 한도는_1_이상이어야_한다() {
        assertThrows(IllegalArgumentException.class, () -> store(0));
    }
}
