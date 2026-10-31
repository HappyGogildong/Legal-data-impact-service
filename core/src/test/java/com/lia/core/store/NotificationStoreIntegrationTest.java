package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.store.NotificationStore.NotificationRow;
import com.lia.core.testsupport.TestDatabases;

/** 구독 알림(D74) — 시점 경계·놓친 날 보충·정확히 한 번·시행예정만·시점 변경·새 개정·목록·읽음. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class NotificationStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate EFFECTIVE = LocalDate.of(2026, 11, 9);

    static JdbcClient jdbc;
    NotificationStore store;
    UUID userId;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
    }

    @BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM notifications").update();
        jdbc.sql("DELETE FROM law_subscriptions").update();
        jdbc.sql("DELETE FROM law_versions").update();
        store = new NotificationStore(jdbc);
        userId = account();
        version("A", EFFECTIVE, "시행예정", "가법");
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (user_id, provider, provider_id, created_at) VALUES (:id, 'kakao', :sub, now())")
                .param("id", id).param("sub", id.toString()).update();
        return id;
    }

    private void version(String lawId, LocalDate effectiveDate, String status, String title) {
        jdbc.sql("""
                INSERT INTO law_versions (law_id, effective_date, revision, status, title, payload, last_seen)
                VALUES (:lawId, :efYd, 'rev', :status, :title, '{}'::jsonb, now())
                """)
                .param("lawId", lawId).param("efYd", effectiveDate).param("status", status).param("title", title).update();
    }

    private void subscribe(UUID user, String lawId, int notifyBefore) {
        jdbc.sql("""
                INSERT INTO law_subscriptions (user_id, law_id, notify_before, created_at) VALUES (:u, :l, :n, now())
                ON CONFLICT (user_id, law_id) DO UPDATE SET notify_before = :n
                """).param("u", user).param("l", lawId).param("n", notifyBefore).update();
    }

    private int count() {
        return jdbc.sql("SELECT count(*) FROM notifications").query(Integer.class).single();
    }

    @Test
    void 시점_전날엔_없고_시점_당일부터_만들어진다() {
        subscribe(userId, "A", 7);

        assertEquals(0, store.createDue(EFFECTIVE.minusDays(8)));
        assertEquals(1, store.createDue(EFFECTIVE.minusDays(7)));
        assertEquals(1, count());
    }

    @Test
    void 배치가_빠진_날이_있어도_다음_실행에서_한_번_시행일_다음_날엔_없다() {
        subscribe(userId, "A", 7);

        assertEquals(1, store.createDue(EFFECTIVE.minusDays(3)), "늦게 돌아도 시행일 전이면 만든다");
        assertEquals(0, store.createDue(EFFECTIVE.minusDays(2)), "두 번째 실행은 만들지 않는다(정확히 한 번)");
        jdbc.sql("DELETE FROM notifications").update();
        assertEquals(0, store.createDue(EFFECTIVE.plusDays(1)), "시행일이 지나면 만들지 않는다");
    }

    @Test
    void 당일_시점은_시행일에만() {
        subscribe(userId, "A", 0);
        assertEquals(0, store.createDue(EFFECTIVE.minusDays(1)));
        assertEquals(1, store.createDue(EFFECTIVE));
    }

    @Test
    void 시행예정이_아닌_버전은_제외() {
        version("B", EFFECTIVE, "철회", "나법");
        version("C", EFFECTIVE, "시행중", "다법");
        subscribe(userId, "B", 7);
        subscribe(userId, "C", 7);
        assertEquals(0, store.createDue(EFFECTIVE.minusDays(1)));
    }

    @Test
    void 시점을_바꾸면_새_시점_알림이_다시_간다() {
        subscribe(userId, "A", 7);
        store.createDue(EFFECTIVE.minusDays(7));
        subscribe(userId, "A", 1);
        assertEquals(1, store.createDue(EFFECTIVE.minusDays(1)));
        assertEquals(2, count());
    }

    @Test
    void 시점_안으로_늦게_들어온_새_개정도_바로_한_번() {
        subscribe(userId, "A", 30);
        store.createDue(EFFECTIVE.minusDays(30));
        version("A", EFFECTIVE.plusDays(10), "시행예정", "가법");   // 새 개정 — 오늘 기준 이미 30일 안
        assertEquals(1, store.createDue(EFFECTIVE.minusDays(20)));
    }

    @Test
    void countDue_는_만들지_않고_새로_생길_수만() {
        subscribe(userId, "A", 7);
        subscribe(account(), "A", 7);
        assertEquals(2, store.countDue(EFFECTIVE.minusDays(1)));
        assertEquals(0, count());
        store.createDue(EFFECTIVE.minusDays(1));
        assertEquals(0, store.countDue(EFFECTIVE.minusDays(1)));
    }

    @Test
    void 목록은_최근_순_제한_개수_읽지_않은_수_읽음_모두_읽음_남의_것은_무관() {
        version("B", EFFECTIVE.plusDays(1), "시행예정", "나법");
        subscribe(userId, "A", 7);
        subscribe(userId, "B", 7);
        UUID other = account();
        subscribe(other, "A", 7);
        store.createDue(EFFECTIVE.minusDays(1));
        jdbc.sql("UPDATE notifications SET created_at = now() - interval '1 hour' WHERE law_id = 'A'").update();

        List<NotificationRow> mine = store.list(userId, 50);
        assertEquals(List.of("B", "A"), mine.stream().map(NotificationRow::lawId).toList());
        assertEquals("가법", mine.get(1).title());
        assertEquals(1, store.list(userId, 1).size());
        assertEquals(2, store.unreadCount(userId));

        long otherId = store.list(other, 50).getFirst().id();
        store.markRead(userId, otherId);
        assertEquals(1, store.unreadCount(other), "남의 알림은 읽음 처리되지 않는다");

        store.markRead(userId, mine.getFirst().id());
        store.markRead(userId, mine.getFirst().id());
        assertEquals(1, store.unreadCount(userId));
        assertTrue(store.list(userId, 50).getFirst().read());

        store.markAllRead(userId);
        assertEquals(0, store.unreadCount(userId));
        assertEquals(1, store.unreadCount(other));
    }

    @Test
    void 계정을_지우면_알림도_지워진다() {
        subscribe(userId, "A", 7);
        store.createDue(EFFECTIVE.minusDays(1));
        jdbc.sql("DELETE FROM accounts WHERE user_id = :id").param("id", userId).update();
        assertEquals(0, count());
    }
}
