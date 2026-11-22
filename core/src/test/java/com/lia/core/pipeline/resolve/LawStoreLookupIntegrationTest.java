package com.lia.core.pipeline.resolve;

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
import com.lia.core.lawdata.LawStore;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.testsupport.TestDatabases;
import com.lia.core.testsupport.TestLaws;

/** 해소용 법령명 조회 — 적재된 시행예정본 제목에서 찾는다(D87). Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class LawStoreLookupIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    static JdbcClient jdbc;
    static LawStore lawStore;
    static LawStoreLookup lookup;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        lawStore = new LawStore(jdbc, new ObjectMapper());
        lookup = new LawStoreLookup(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM law_versions").update();
    }

    private void save(String lawId, String title, Law.Status status, LocalDate effectiveDate) {
        lawStore.upsert(TestLaws.law(lawId, "M-" + lawId + "-" + effectiveDate, title, status, effectiveDate));
    }

    private List<String> titles(String query, int limit) {
        return lookup.searchByName(query, limit).stream().map(RawLaw::title).toList();
    }

    @Test
    @DisplayName("오늘(서울) 이후 시행예정만 — 지난 시행예정·시행중·다른 이름은 빠진다")
    void 오늘_이후_시행예정만() {
        save("A", "주택법", Law.Status.시행예정, TODAY.plusDays(10));
        save("B", "주택법 시행령", Law.Status.시행예정, TODAY);              // 오늘 시행 — 포함
        save("C", "주택법 시행규칙", Law.Status.시행예정, TODAY.minusDays(1)); // 전환 배치 전 — 뺀다
        save("D", "주택법", Law.Status.시행중, TODAY.plusDays(3));
        save("E", "건축법", Law.Status.시행예정, TODAY.plusDays(5));

        List<String> ids = lookup.searchByName("주택법", 10).stream().map(RawLaw::lawId).toList();

        assertEquals(List.of("A", "B"), ids);
    }

    @Test
    @DisplayName("정렬 — 정확히 같은 제목 먼저, 그다음 짧은 제목, 같으면 시행일 순")
    void 정확한_제목_먼저() {
        save("P", "약사법 시행규칙", Law.Status.시행예정, TODAY.plusDays(1));
        save("Q", "약사법 시행령", Law.Status.시행예정, TODAY.plusDays(2));
        save("R", "약사법", Law.Status.시행예정, TODAY.plusDays(30));
        save("R", "약사법", Law.Status.시행예정, TODAY.plusDays(20));

        List<RawLaw> found = lookup.searchByName("약사법", 10);

        assertEquals(List.of("약사법", "약사법", "약사법 시행령", "약사법 시행규칙"),
                found.stream().map(RawLaw::title).toList());
        assertEquals(List.of(TODAY.plusDays(20), TODAY.plusDays(30)),
                found.subList(0, 2).stream().map(RawLaw::effectiveDate).toList());
    }

    @Test
    @DisplayName("한도를 넘어도 정확한 제목은 남는다 — 시행일이 가장 늦어도")
    void 한도를_넘어도_정확한_제목은_남는다() {
        for (int i = 1; i <= 12; i++) {
            save("S" + i, "도시 주택법 특례 " + i, Law.Status.시행예정, TODAY.plusDays(i));
        }
        save("H", "주택법", Law.Status.시행예정, TODAY.plusDays(100));

        List<String> found = titles("주택법", 10);

        assertEquals(10, found.size());
        assertEquals("주택법", found.get(0));
    }

    @Test
    @DisplayName("검색어는 글자 그대로 — % · _ 는 와일드카드가 아니고, 앞뒤 공백은 지운다")
    void 글자_그대로_포함() {
        save("A", "주택법", Law.Status.시행예정, TODAY.plusDays(1));

        assertEquals(List.of(), titles("%", 10));
        assertEquals(List.of(), titles("주_법", 10));
        assertEquals(List.of("주택법"), titles("택법", 10));
        assertEquals(List.of("주택법"), titles("  주택법 ", 10));
    }

    @Test
    @DisplayName("후보 필드 — 컬럼과 payload 의 mst·공포일·공포번호, 본문(raw)은 없다")
    void 후보_필드() {
        LocalDate effective = TODAY.plusDays(7);
        save("A", "주택법", Law.Status.시행예정, effective);

        RawLaw found = lookup.searchByName("주택법", 10).get(0);

        assertEquals(new RawLaw("A", "M-A-" + effective, "주택법", "시행예정", effective,
                LocalDate.of(2026, 1, 1), "1", null), found);
    }

    @Test
    @DisplayName("빈 검색어·0 한도는 빈 목록")
    void 빈_검색어와_0_한도() {
        save("A", "주택법", Law.Status.시행예정, TODAY.plusDays(1));

        assertEquals(List.of(), lookup.searchByName(null, 10));
        assertEquals(List.of(), lookup.searchByName("  ", 10));
        assertEquals(List.of(), lookup.searchByName("주택법", 0));
    }
}
