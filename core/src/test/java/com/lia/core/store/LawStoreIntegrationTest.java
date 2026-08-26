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
}
