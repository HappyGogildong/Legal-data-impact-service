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

/** 법령 목록·시행예정본 목록 — 오늘(서울) 기준 필터·정렬·커서. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class LawCatalogStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    static JdbcClient jdbc;
    static LawStore lawStore;
    static LawCatalogStore catalog;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        lawStore = new LawStore(jdbc, new ObjectMapper());
        catalog = new LawCatalogStore(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM law_versions").update();
    }

    private void save(String lawId, Law.Status status, LocalDate effectiveDate) {
        lawStore.upsert(TestLaws.law(lawId, "M-" + lawId + "-" + effectiveDate, status, effectiveDate));
    }

    private static List<String> ids(LawCatalogStore.LawListPage page) {
        return page.items().stream().map(LawCatalogStore.LawListItem::lawId).toList();
    }

    @Test
    @DisplayName("오늘(서울) 이후 시행예정만 — 시행일, 같으면 lawId 순 · 필드는 payload 에서")
    void 오늘_이후_시행예정만() {
        save("B", Law.Status.시행예정, TODAY.plusDays(10));
        save("A", Law.Status.시행예정, TODAY.plusDays(10));
        save("C", Law.Status.시행예정, TODAY.plusDays(5));
        save("D", Law.Status.시행예정, TODAY.minusDays(1));   // 전환 배치 전 — 목록에서 뺀다
        save("E", Law.Status.시행중, TODAY.plusDays(3));
        save("F", Law.Status.철회, TODAY.plusDays(4));

        LawCatalogStore.LawListPage page = catalog.listPending(null, null, null, 20);

        assertEquals(List.of("C", "A", "B"), ids(page));
        assertNull(page.nextCursor());
        assertEquals(new LawCatalogStore.LawListItem("C", "테스트법", TODAY.plusDays(5), "법률", "부처", "일부개정"),
                page.items().get(0));
    }

    @Test
    @DisplayName("기간 — from·to 포함, from 이 오늘보다 이르면 오늘부터")
    void 기간_범위() {
        save("X", Law.Status.시행예정, TODAY.plusDays(3));
        save("Y", Law.Status.시행예정, TODAY.plusDays(20));
        save("Z", Law.Status.시행예정, TODAY.plusDays(60));
        save("P", Law.Status.시행예정, TODAY.minusDays(2));

        assertEquals(List.of("Y"), ids(catalog.listPending(TODAY.plusDays(7), TODAY.plusDays(20), null, 20)));
        assertEquals(List.of("X"), ids(catalog.listPending(TODAY.minusDays(30), TODAY.plusDays(7), null, 20)));
    }

    @Test
    @DisplayName("커서 — 같은 시행일은 lawId 로 이어지고 마지막 쪽은 nextCursor 없음")
    void 커서_쪽_넘김() {
        save("A", Law.Status.시행예정, TODAY.plusDays(1));
        save("B1", Law.Status.시행예정, TODAY.plusDays(2));
        save("B2", Law.Status.시행예정, TODAY.plusDays(2));
        save("C", Law.Status.시행예정, TODAY.plusDays(3));

        LawCatalogStore.LawListPage first = catalog.listPending(null, null, null, 2);
        assertEquals(List.of("A", "B1"), ids(first));
        assertNotNull(first.nextCursor());

        LawCatalogStore.LawListPage second =
                catalog.listPending(null, null, LawCatalogStore.Cursor.decode(first.nextCursor()), 2);
        assertEquals(List.of("B2", "C"), ids(second));
        assertNull(second.nextCursor());
    }

    @Test
    @DisplayName("시행예정본 목록 — 오늘 이후 시행예정만 시행일 순, 기준선 = 가장 최근 시행중본")
    void 시행예정본_목록() {
        save("L", Law.Status.시행예정, TODAY.plusDays(40));
        save("L", Law.Status.시행예정, TODAY.plusDays(10));
        save("L", Law.Status.시행예정, TODAY.minusDays(1));
        save("L", Law.Status.시행중, LocalDate.of(2020, 1, 1));
        save("L", Law.Status.시행중, LocalDate.of(2023, 1, 1));
        save("L", Law.Status.과거, LocalDate.of(2019, 1, 1));

        LawCatalogStore.LawVersions versions = catalog.versions("L").orElseThrow();

        assertEquals("L", versions.lawId());
        assertEquals("테스트법", versions.title());
        assertEquals(List.of(
                new LawCatalogStore.UpcomingVersion(TODAY.plusDays(10), "1", "일부개정"),
                new LawCatalogStore.UpcomingVersion(TODAY.plusDays(40), "1", "일부개정")), versions.pending());
        assertEquals(new LawCatalogStore.InForceVersion(LocalDate.of(2023, 1, 1), "1"), versions.current());
    }

    @Test
    @DisplayName("없는 법령 → empty, 시행중본이 없으면 current=null")
    void 없는_법령() {
        save("N", Law.Status.시행예정, TODAY.plusDays(5));

        assertTrue(catalog.versions("NONE").isEmpty());
        assertNull(catalog.versions("N").orElseThrow().current());
    }
}
