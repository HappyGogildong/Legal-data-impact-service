package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

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
import com.lia.core.testsupport.TestDatabases;
import com.lia.core.testsupport.TestLaws;

/** 조회 집계 — 서울 오늘 증가·구간 합산 순위·직전 순위·필터. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class LawViewStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    static final LocalDate SOON = TODAY.plusDays(30);

    static JdbcClient jdbc;
    static LawStore lawStore;
    static LawViewStore views;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        lawStore = new LawStore(jdbc, new ObjectMapper());
        views = new LawViewStore(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM law_views_daily").update();
        jdbc.sql("DELETE FROM law_versions").update();
    }

    private void save(String lawId, Law.Status status, LocalDate effectiveDate) {
        lawStore.upsert(TestLaws.law(lawId, "M-" + lawId + "-" + effectiveDate, status, effectiveDate));
    }

    /** 지난 날짜의 조회를 직접 심는다(record 는 오늘만 쓴다). */
    private void seed(String lawId, LocalDate effectiveDate, LocalDate day, int count) {
        jdbc.sql("INSERT INTO law_views_daily (law_id, effective_date, view_date, views) VALUES (:l, :e, :d, :v)")
                .param("l", lawId).param("e", effectiveDate).param("d", day).param("v", count).update();
    }

    private static List<String> ids(LawViewStore.TrendingPage page) {
        return page.items().stream().map(LawViewStore.TrendingItem::lawId).toList();
    }

    @Test
    @DisplayName("record — 같은 날 같은 버전은 한 행에서 증가, 없는 버전은 false·행 없음")
    void 기록() {
        save("A", Law.Status.시행예정, SOON);

        assertTrue(views.record("A", SOON));
        assertTrue(views.record("A", SOON));
        assertFalse(views.record("NONE", SOON));

        assertEquals(2, jdbc.sql("SELECT views FROM law_views_daily WHERE law_id = 'A' AND view_date = :d")
                .param("d", TODAY).query(Integer.class).single());
        assertEquals(1, jdbc.sql("SELECT count(*) FROM law_views_daily").query(Integer.class).single());
    }

    @Test
    @DisplayName("week — 오늘 포함 7일 합산 순위, 그 밖의 날은 빼고, 동점은 lawId 순")
    void 주간_순위() {
        save("A", Law.Status.시행예정, SOON);
        save("B", Law.Status.시행예정, SOON);
        save("C", Law.Status.시행예정, SOON);
        seed("A", SOON, TODAY, 3);
        seed("A", SOON, TODAY.minusDays(6), 2);    // 구간 안(7일째)
        seed("B", SOON, TODAY.minusDays(1), 5);
        seed("C", SOON, TODAY.minusDays(7), 50);   // 구간 밖(8일째)
        seed("C", SOON, TODAY, 5);

        LawViewStore.TrendingPage page = views.trending(TrendingWindow.week, 10);

        assertEquals("week", page.window());
        assertEquals(List.of("A", "B", "C"), ids(page));
        assertEquals(new LawViewStore.TrendingItem(1, "A", "테스트법", SOON, 5, null), page.items().get(0));
        assertEquals(2, page.items().get(1).rank());
        assertEquals(5, page.items().get(2).views());
    }

    @Test
    @DisplayName("previousRank — 직전 같은 길이 구간 순위, 없으면 null")
    void 직전_순위() {
        save("A", Law.Status.시행예정, SOON);
        save("B", Law.Status.시행예정, SOON);
        save("N", Law.Status.시행예정, SOON);
        seed("A", SOON, TODAY, 10);
        seed("B", SOON, TODAY, 20);
        seed("N", SOON, TODAY, 1);
        seed("A", SOON, TODAY.minusDays(8), 30);   // 직전 주(8~14일 전)
        seed("B", SOON, TODAY.minusDays(10), 5);

        List<LawViewStore.TrendingItem> items = views.trending(TrendingWindow.week, 10).items();

        assertEquals(List.of("B", "A", "N"), items.stream().map(LawViewStore.TrendingItem::lawId).toList());
        assertEquals(2, items.get(0).previousRank());   // B: 직전 2위
        assertEquals(1, items.get(1).previousRank());   // A: 직전 1위
        assertNull(items.get(2).previousRank());        // N: 직전 없음
    }

    @Test
    @DisplayName("시행예정·오늘 이후 버전만, day 는 오늘만, limit")
    void 필터와_구간() {
        save("P", Law.Status.시행예정, SOON);
        save("Q", Law.Status.시행예정, SOON);
        save("X", Law.Status.시행중, SOON);
        save("Y", Law.Status.시행예정, TODAY.minusDays(1));
        seed("P", SOON, TODAY, 1);
        seed("Q", SOON, TODAY.minusDays(1), 9);
        seed("X", SOON, TODAY, 99);
        seed("Y", TODAY.minusDays(1), TODAY, 99);

        assertEquals(List.of("P"), ids(views.trending(TrendingWindow.day, 10)));
        assertEquals(List.of("Q", "P"), ids(views.trending(TrendingWindow.week, 10)));
        assertEquals(List.of("Q"), ids(views.trending(TrendingWindow.week, 1)));
    }
}
