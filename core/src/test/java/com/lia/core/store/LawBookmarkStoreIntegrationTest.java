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

import com.lia.core.domain.law.Law;
import com.lia.core.store.LawBookmarkStore.AddResult;
import com.lia.core.store.LawBookmarkStore.BookmarkedLaw;
import com.lia.core.testsupport.TestDatabases;

/** 관심 법령 북마크(D72) — 멱등·한도·법령 존재·cascade·보여 줄 버전. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class LawBookmarkStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

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
        jdbc.sql("DELETE FROM law_bookmarks").update();
        jdbc.sql("DELETE FROM law_versions").update();
        userId = account();
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (user_id, provider, provider_id, created_at) VALUES (:id, 'kakao', :sub, now())")
                .param("id", id).param("sub", id.toString()).update();
        return id;
    }

    private void version(String lawId, String effectiveDate, String status, String title) {
        jdbc.sql("""
                INSERT INTO law_versions (law_id, effective_date, revision, status, title, payload, last_seen)
                VALUES (:lawId, :efYd, 'rev', :status, :title, '{}'::jsonb, now())
                """)
                .param("lawId", lawId).param("efYd", LocalDate.parse(effectiveDate))
                .param("status", status).param("title", title).update();
    }

    private LawBookmarkStore store(int max) {
        return new LawBookmarkStore(jdbc, transactions, max);
    }

    @Test
    void 추가는_멱등이고_해제는_없어도_성공한다() {
        version("001809", "2026-11-08", "시행예정", "주택법");
        LawBookmarkStore store = store(10);

        assertEquals(AddResult.ADDED, store.add(userId, "001809"));
        assertEquals(AddResult.ALREADY, store.add(userId, "001809"));
        assertEquals(1, store.list(userId).size());

        store.remove(userId, "001809");
        store.remove(userId, "001809");
        assertTrue(store.list(userId).isEmpty());
    }

    @Test
    void 저장된_버전이_없는_법령은_추가하지_않는다() {
        assertEquals(AddResult.LAW_NOT_FOUND, store(10).add(userId, "999999"));
        assertEquals(0, jdbc.sql("SELECT count(*) FROM law_bookmarks").query(Integer.class).single());
    }

    @Test
    void 한도에_닿으면_새_법령은_거부하고_이미_있는_것은_한도와_무관하게_ALREADY() {
        version("A", "2026-11-01", "시행예정", "가법");
        version("B", "2026-11-01", "시행예정", "나법");
        version("C", "2026-11-01", "시행예정", "다법");
        LawBookmarkStore store = store(2);

        assertEquals(AddResult.ADDED, store.add(userId, "A"));
        assertEquals(AddResult.ADDED, store.add(userId, "B"));
        assertEquals(AddResult.LIMIT, store.add(userId, "C"));
        assertEquals(AddResult.ALREADY, store.add(userId, "A"));
        assertEquals(2, store.list(userId).size());
    }

    @Test
    void 한도는_사용자마다_따로_센다() {
        version("A", "2026-11-01", "시행예정", "가법");
        LawBookmarkStore store = store(1);
        UUID other = account();

        assertEquals(AddResult.ADDED, store.add(userId, "A"));
        assertEquals(AddResult.ADDED, store.add(other, "A"));
    }

    @Test
    void 계정이_없으면_ACCOUNT_NOT_FOUND() {
        version("A", "2026-11-01", "시행예정", "가법");
        assertEquals(AddResult.ACCOUNT_NOT_FOUND, store(10).add(UUID.randomUUID(), "A"));
    }

    @Test
    void 계정을_지우면_북마크도_지워진다() {
        version("A", "2026-11-01", "시행예정", "가법");
        store(10).add(userId, "A");

        jdbc.sql("DELETE FROM accounts WHERE user_id = :id").param("id", userId).update();

        assertEquals(0, jdbc.sql("SELECT count(*) FROM law_bookmarks").query(Integer.class).single());
    }

    @Test
    void 목록은_추가한_시각_최근_순이고_내_것만() {
        version("A", "2026-11-01", "시행예정", "가법");
        version("B", "2026-11-01", "시행예정", "나법");
        LawBookmarkStore store = store(10);
        store.add(userId, "A");
        jdbc.sql("UPDATE law_bookmarks SET created_at = now() - interval '1 hour' WHERE law_id = 'A'").update();
        store.add(userId, "B");
        store.add(account(), "A");

        assertEquals(List.of("B", "A"), store.list(userId).stream().map(BookmarkedLaw::lawId).toList());
    }

    @Test
    void 보여_줄_버전_시행예정본이_여럿이면_가장_이른_것() {
        version("A", "2020-01-01", "시행중", "가법(현행)");
        version("A", "2027-03-01", "시행예정", "가법(나중)");
        version("A", "2026-12-01", "시행예정", "가법(먼저)");
        LawBookmarkStore store = store(10);
        store.add(userId, "A");

        BookmarkedLaw shown = store.list(userId).getFirst();
        assertEquals(Law.Status.시행예정, shown.status());
        assertEquals(LocalDate.parse("2026-12-01"), shown.effectiveDate());
        assertEquals("가법(먼저)", shown.title());
        assertNotNull(shown.bookmarkedAt());
    }

    @Test
    void 보여_줄_버전_시행예정본이_없으면_시행일이_가장_늦은_행() {
        version("A", "2020-01-01", "과거", "가법");
        version("A", "2026-08-04", "시행중", "가법");
        version("B", "2020-01-01", "시행중", "나법");
        version("B", "2026-12-01", "철회", "나법");
        version("C", "2019-01-01", "과거", "다법");
        LawBookmarkStore store = store(10);
        store.add(userId, "A");
        store.add(userId, "B");
        store.add(userId, "C");

        List<BookmarkedLaw> shown = store.list(userId);
        BookmarkedLaw a = shown.stream().filter(law -> law.lawId().equals("A")).findFirst().orElseThrow();
        BookmarkedLaw b = shown.stream().filter(law -> law.lawId().equals("B")).findFirst().orElseThrow();
        BookmarkedLaw c = shown.stream().filter(law -> law.lawId().equals("C")).findFirst().orElseThrow();
        assertEquals(Law.Status.시행중, a.status());
        assertEquals(LocalDate.parse("2026-08-04"), a.effectiveDate());
        assertEquals(Law.Status.철회, b.status());
        assertEquals(LocalDate.parse("2026-12-01"), b.effectiveDate());
        assertEquals(Law.Status.과거, c.status());
    }

    @Test
    void 한도는_1_이상이어야_한다() {
        assertThrows(IllegalArgumentException.class, () -> store(0));
    }
}
