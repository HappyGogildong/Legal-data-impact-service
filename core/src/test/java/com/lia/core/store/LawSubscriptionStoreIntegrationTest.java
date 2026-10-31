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

import com.lia.core.store.LawSubscriptionStore.SubscribeResult;
import com.lia.core.store.LawSubscriptionStore.SubscribedLaw;
import com.lia.core.testsupport.TestDatabases;

/** 법령 구독(D74) — 추가·시점 변경(한도 무관)·해지·한도·법령 없음·cascade·목록(다음 시행일·정렬). Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class LawSubscriptionStoreIntegrationTest {

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
        jdbc.sql("DELETE FROM law_subscriptions").update();
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

    private LawSubscriptionStore store(int max) {
        return new LawSubscriptionStore(jdbc, transactions, max);
    }

    @Test
    void 구독하고_다시_구독하면_시점만_바뀐다() {
        version("A", "2026-11-09", "시행예정", "가법");
        LawSubscriptionStore store = store(10);

        assertEquals(SubscribeResult.SUBSCRIBED, store.subscribe(userId, "A", 7));
        assertEquals(SubscribeResult.UPDATED, store.subscribe(userId, "A", 1));

        List<SubscribedLaw> list = store.list(userId);
        assertEquals(1, list.size());
        assertEquals(1, list.getFirst().notifyBefore());
    }

    @Test
    void 해지는_멱등() {
        version("A", "2026-11-09", "시행예정", "가법");
        LawSubscriptionStore store = store(10);
        store.subscribe(userId, "A", 7);

        store.unsubscribe(userId, "A");
        store.unsubscribe(userId, "A");
        assertTrue(store.list(userId).isEmpty());
    }

    @Test
    void 한도에_닿으면_새_법령은_거부하고_이미_구독한_법령의_시점_변경은_허용() {
        version("A", "2026-11-09", "시행예정", "가법");
        version("B", "2026-11-09", "시행예정", "나법");
        LawSubscriptionStore store = store(1);
        store.subscribe(userId, "A", 7);

        assertEquals(SubscribeResult.LIMIT, store.subscribe(userId, "B", 7));
        assertEquals(SubscribeResult.UPDATED, store.subscribe(userId, "A", 30));
    }

    @Test
    void 저장된_버전이_없는_법령은_LAW_NOT_FOUND_계정이_없으면_ACCOUNT_NOT_FOUND() {
        version("A", "2026-11-09", "시행예정", "가법");
        assertEquals(SubscribeResult.LAW_NOT_FOUND, store(10).subscribe(userId, "Z", 7));
        assertEquals(SubscribeResult.ACCOUNT_NOT_FOUND, store(10).subscribe(UUID.randomUUID(), "A", 7));
    }

    @Test
    void 계정을_지우면_구독도_지워진다() {
        version("A", "2026-11-09", "시행예정", "가법");
        store(10).subscribe(userId, "A", 7);
        jdbc.sql("DELETE FROM accounts WHERE user_id = :id").param("id", userId).update();
        assertEquals(0, jdbc.sql("SELECT count(*) FROM law_subscriptions").query(Integer.class).single());
    }

    @Test
    void 목록은_다음_시행일_가까운_순_없는_것은_뒤_법령명은_보여_줄_버전() {
        version("A", "2026-12-01", "시행예정", "가법(나중)");
        version("A", "2026-11-20", "시행예정", "가법(먼저)");
        version("B", "2026-11-10", "시행예정", "나법");
        version("C", "2026-08-04", "시행중", "다법");
        LawSubscriptionStore store = store(10);
        store.subscribe(userId, "C", 7);
        store.subscribe(userId, "A", 7);
        store.subscribe(userId, "B", 7);
        store.subscribe(account(), "B", 7);

        List<SubscribedLaw> list = store.list(userId);
        assertEquals(List.of("B", "A", "C"), list.stream().map(SubscribedLaw::lawId).toList());
        assertEquals(LocalDate.parse("2026-11-20"), list.get(1).nextEffectiveDate());
        assertEquals("가법(먼저)", list.get(1).title());
        assertNull(list.get(2).nextEffectiveDate());
        assertEquals("다법", list.get(2).title());
    }

    @Test
    void 허용_밖_시점은_DB_가_거부한다() {
        version("A", "2026-11-09", "시행예정", "가법");
        assertThrows(RuntimeException.class, () -> store(10).subscribe(userId, "A", 3));
    }

    @Test
    void 한도는_1_이상이어야_한다() {
        assertThrows(IllegalArgumentException.class, () -> store(0));
    }
}
