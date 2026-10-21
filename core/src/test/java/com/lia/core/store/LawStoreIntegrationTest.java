package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;

/**
 * LawStore 통합 테스트 — 실 Postgres(Testcontainers)에 upsert·find·findBaseline.
 * {@code @SpringBootTest} 가 아니라 자체 DataSource+Flyway 를 써서 다른 테스트 컨텍스트에 무영향.
 * Docker 없으면 자동 스킵.
 */
@Testcontainers(disabledWithoutDocker = true)
class LawStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static LawStore store;

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        store = new LawStore(JdbcClient.create(ds), new ObjectMapper());
    }

    // --- 픽스처 ------------------------------------------------------------

    private static Law law(String lawId, Law.Status status, LocalDate ef, String revision, String... changed) {
        var articles = List.of(new Article("18", "제18조", "제18조 본문 " + revision,
                changed.length > 0, ChangeType.개정, null, null, ef, true,
                changed.length > 0 ? changed[0] : null));
        return new Law(lawId, "MST-" + revision, "주택법", status,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", ef,
                "이 법은 공포 후 6개월…", Law.EnforcementType.유예, "개정이유", "개정문",
                List.of(), articles, List.of("대통령령"), null, "url", revision, Instant.now());
    }

    // --- 테스트 -----------------------------------------------------------

    @Test
    @DisplayName("정본 upsert 후 find 로 JSONB 라운드트립(중첩·enum·날짜 보존)")
    void upsert_find_라운드트립() {
        Law pending = law("001809", Law.Status.시행예정, LocalDate.of(2026, 8, 4), "revA", "[개정] 현행→개정");
        store.upsert(pending);

        Optional<Law> got = store.find("001809", LocalDate.of(2026, 8, 4));

        assertTrue(got.isPresent());
        Law r = got.get();
        assertEquals("001809", r.lawId());
        assertEquals(Law.Status.시행예정, r.status());
        assertEquals("revA", r.revision());
        assertEquals(1, r.articles().size());
        assertEquals("[개정] 현행→개정", r.articles().get(0).diffVsCurrent(), "중첩 조문·diff 필드 보존");
        assertEquals(LocalDate.of(2026, 8, 4), r.effectiveDate());
        assertEquals(Law.EnforcementType.유예, r.enforcementType());
    }

    @Test
    @DisplayName("findBaseline — 시행중본이 있으면 그것을, 제정(없음)이면 empty")
    void findBaseline_현행유무() {
        store.upsert(law("010513", Law.Status.시행중, LocalDate.of(2020, 1, 1), "cur"));
        store.upsert(law("010513", Law.Status.시행예정, LocalDate.of(2026, 10, 1), "pend"));

        Optional<Law> baseline = store.findBaseline("010513");
        assertTrue(baseline.isPresent(), "시행중본이 있어야 한다");
        assertEquals(Law.Status.시행중, baseline.get().status());

        // 제정: 시행예정만 있고 시행중본 없음 → empty
        store.upsert(law("099999", Law.Status.시행예정, LocalDate.of(2026, 11, 1), "new"));
        assertTrue(store.findBaseline("099999").isEmpty(), "제정은 기준선 없음");
    }

    @Test
    @DisplayName("같은 (lawId, efYd) 재upsert 는 멱등 — revision 갱신")
    void upsert_멱등() {
        LocalDate ef = LocalDate.of(2027, 3, 1);
        store.upsert(law("001809", Law.Status.시행예정, ef, "rev1"));
        store.upsert(law("001809", Law.Status.시행예정, ef, "rev2"));   // 같은 키, 새 revision

        Long count = store.find("001809", ef).isPresent() ? 1L : 0L;
        assertEquals(1L, count, "중복 행이 아니라 갱신");
        assertEquals("rev2", store.find("001809", ef).get().revision());
    }

    @Test
    @DisplayName("revisionOf — 상태가 같을 때만 revision, 행이 없거나 상태가 다르면 empty")
    void revisionOf() {
        LocalDate ef = LocalDate.of(2027, 1, 1);
        store.upsert(law("900001", Law.Status.시행예정, ef, "rev-a"));

        assertEquals(Optional.of("rev-a"), store.revisionOf("900001", ef, Law.Status.시행예정));
        assertTrue(store.revisionOf("900001", LocalDate.of(2027, 2, 2), Law.Status.시행예정).isEmpty());
        assertTrue(store.revisionOf("900001", ef, Law.Status.철회).isEmpty(), "상태가 다르면 '다름'으로 본다");
    }

    @Test
    @DisplayName("listPendingVersions·findPendingVersions — 시행예정만, mst·missing_since 포함")
    void pendingVersions() {
        store.upsert(law("900002", Law.Status.시행예정, LocalDate.of(2027, 3, 1), "p1"));
        store.upsert(law("900002", Law.Status.시행예정, LocalDate.of(2027, 6, 1), "p2"));
        store.upsert(law("900002", Law.Status.시행중, LocalDate.of(2020, 1, 1), "cur"));

        List<PendingVersion> mine = store.listPendingVersions().stream()
                .filter(v -> v.lawId().equals("900002")).toList();
        assertEquals(2, mine.size(), "시행중본은 제외");
        assertEquals("MST-p1", mine.get(0).mst());
        assertNull(mine.get(0).missingSince());

        assertEquals(List.of(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 6, 1)),
                store.findPendingVersions("900002").stream().map(PendingVersion::effectiveDate).toList());
    }

    @Test
    @DisplayName("updateStatus — 컬럼과 payload 상태가 함께 바뀌어 find 결과에도 반영된다")
    void updateStatus() {
        LocalDate ef = LocalDate.of(2027, 4, 1);
        store.upsert(law("900003", Law.Status.시행예정, ef, "r"));

        store.updateStatus("900003", ef, Law.Status.철회);

        assertEquals(Law.Status.철회, store.find("900003", ef).orElseThrow().status(), "payload 상태");
        assertTrue(store.findPendingVersions("900003").isEmpty(), "컬럼 상태 — 시행예정 목록에서 빠짐");
    }

    @Test
    @DisplayName("supersedeOlderInForce — 기준일보다 오래된 시행중만 과거로, findBaseline 은 최신 시행중")
    void supersedeOlderInForce() {
        store.upsert(law("900004", Law.Status.시행중, LocalDate.of(2019, 1, 1), "old"));
        store.upsert(law("900004", Law.Status.시행중, LocalDate.of(2021, 1, 1), "new"));

        int changed = store.supersedeOlderInForce("900004", LocalDate.of(2021, 1, 1));

        assertEquals(1, changed);
        assertEquals(Law.Status.과거, store.find("900004", LocalDate.of(2019, 1, 1)).orElseThrow().status());
        assertEquals(LocalDate.of(2021, 1, 1), store.findBaseline("900004").orElseThrow().effectiveDate());
    }

    @Test
    @DisplayName("markMissing·clearMissing — 사라짐 판정 날짜 기록·해제")
    void missingSince() {
        LocalDate ef = LocalDate.of(2027, 5, 1);
        store.upsert(law("900005", Law.Status.시행예정, ef, "r"));

        store.markMissing("900005", ef, LocalDate.of(2026, 7, 1));
        assertEquals(LocalDate.of(2026, 7, 1), store.findPendingVersions("900005").get(0).missingSince());

        store.clearMissing("900005", ef);
        assertNull(store.findPendingVersions("900005").get(0).missingSince());
    }
}
