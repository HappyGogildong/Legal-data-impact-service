package com.lia.core.pipeline.resolve;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * 해소 4상태 단위 테스트 (D23). 대상은 시행예정 법령이다(D42 이관).
 */
class SourceAnalyzerTest {

    /** 오프라인 페이크 조회 — 같은 법령ID의 시행예정본 복수 케이스 포함(D43, 실측 재현). */
    static class FakeLookup implements LawLookup {
        static final List<RawLaw> DATA = List.of(
                law("001809", "283191", "주택법", "20260804"),
                law("010513", "283193", "자본시장과 금융투자업에 관한 법률", "20261001"),
                law("010513", "285001", "자본시장과 금융투자업에 관한 법률", "20261113"),
                law("011997", "283203", "아동학대범죄의 처벌 등에 관한 특례법", "20260804"));

        static RawLaw law(String lawId, String mst, String title, String efYd) {
            return new RawLaw(lawId, mst, title, "시행예정",
                    LocalDate.parse(efYd, DateTimeFormatter.BASIC_ISO_DATE),
                    LocalDate.of(2026, 2, 3), "21323", Map.of());
        }

        @Override
        public List<RawLaw> searchByName(String query, int limit) {
            if (query == null || query.isBlank()) return List.of();
            return DATA.stream()
                    .filter(l -> java.util.Arrays.stream(query.split("\\s+")).anyMatch(l.title()::contains))
                    .limit(limit)
                    .toList();
        }
    }

    private final SourceAnalyzer sa = new SourceAnalyzer(new FakeLookup());

    @Test
    void 정확한_법령명은_RESOLVED() {
        var r = sa.resolve("주택법");

        assertEquals(ResolutionState.RESOLVED, r.state());
        assertEquals("001809", r.resolved().lawId());
        assertEquals("시행예정", r.resolved().status());
    }

    @Test
    void 같은_법령의_시행예정본_복수는_가장_이른_시행일로_해소하고_나머지를_안내한다() {   // D43
        var r = sa.resolve("자본시장과 금융투자업에 관한 법률");

        assertEquals(ResolutionState.RESOLVED, r.state(), "복수 시행예정본은 가장 이른 본으로 해소된다(D43)");
        assertEquals("010513", r.resolved().lawId());
        assertEquals(LocalDate.of(2026, 10, 1), r.resolved().effectiveDate(), "가장 이른 시행일본이어야 한다");
        assertEquals(1, r.alternatives().size(), "나머지 시행예정본은 안내(alternatives)로 딸린다");
        assertEquals(LocalDate.of(2026, 11, 13), r.alternatives().get(0).effectiveDate());
    }

    @Test
    void 미등록_법령표현은_NOT_FOUND_YET() {
        var r = sa.resolve("아직 공포 안 된 가상의 무슨무슨 법률");

        assertEquals(ResolutionState.NOT_FOUND_YET, r.state());
        assertNull(r.resolved(), "확인 안 된 입력에 결과를 지어내면 안 된다");
    }

    @Test
    void 법령_아닌_입력은_UNVERIFIED() {
        assertEquals(ResolutionState.UNVERIFIED, sa.resolve("오늘 점심 뭐 먹지").state());
    }

    @Test
    void 미등록과_허위는_안내_문구가_다르다() {
        String notFound = sa.resolve("무슨무슨 법률").message();
        String unverified = sa.resolve("점심 메뉴 추천").message();

        assertNotEquals(notFound, unverified, "D23 — 미등록과 허위 의심은 구분해 안내한다");
        assertTrue(unverified.contains("확인되지 않은"));
    }

    @Test
    void 조회수단이_없어도_fail_closed_지어내지_않음() {
        var empty = new SourceAnalyzer(null);

        assertEquals(ResolutionState.NOT_FOUND_YET, empty.resolve("무슨무슨 법률").state());
        assertEquals(ResolutionState.UNVERIFIED, empty.resolve("점심 메뉴 추천").state());
    }

    @Test
    void 조회가_예외를_던져도_fail_closed로_떨어진다() {
        LawLookup broken = (q, n) -> { throw new IllegalStateException("LAW_OC 미설정"); };
        var sa2 = new SourceAnalyzer(broken);

        assertEquals(ResolutionState.NOT_FOUND_YET, sa2.resolve("주택법").state(),
                "출처 장애가 예외로 새어나가면 안 된다");
    }

    @Test
    void 의미검색이_주입되면_후보를_돌려준다() {
        LawLookup nameMiss = (q, n) -> List.of();
        var withSemantic = new SourceAnalyzer(
                nameMiss,
                text -> List.of(FakeLookup.DATA.get(0)),   // 의미검색만 성공
                88.0, 60.0);

        var r = withSemantic.resolve("사람들이 집 구할 때 뭔가 바뀐다던데");

        assertEquals(ResolutionState.AMBIGUOUS, r.state());
        assertEquals("주택법", r.candidates().get(0).title());
    }
}
