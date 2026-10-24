package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.store.LawSource;
import com.lia.core.web.AnalysisApiResponse.CitedSource;

/** 인용키 → 근거 라벨·원문. 정본은 메모리 LawSource. */
class CitedSourceResolverTest {

    static final LocalDate EF = LocalDate.of(2026, 8, 4);

    /** 조회 횟수를 세는 메모리 정본 — 같은 법령은 요청 안에서 한 번만 읽는지 확인. */
    static final class CountingLawSource implements LawSource {
        final Map<String, Law> pending = new HashMap<>();
        Law baseline;
        int lookups;
        public Optional<Law> find(String lawId, LocalDate efYd) { lookups++; return Optional.ofNullable(pending.get(lawId + "@" + efYd)); }
        public Optional<Law> findBaseline(String lawId) { lookups++; return Optional.ofNullable(baseline); }
    }

    static Law law(Law.Status status, LocalDate ef, String article18Text) {
        List<Article> articles = List.of(
                new Article("18", "사업계획의 통합심의", article18Text, true, Article.ChangeType.개정, null, null, ef, true, null),
                new Article("19", "제19조", "① 그대로다.", false, Article.ChangeType.없음, null, null, ef, true, null));
        return law(status, ef, articles);
    }

    static Law law(Law.Status status, LocalDate ef, List<Article> articles) {
        List<Addendum> addenda = List.of(new Addendum("제1조", "시행일", Addendum.kindOf("시행일"), "이 법은 공포 후 6개월이 경과한 날부터 시행한다.", "21323", ef));
        return new Law("001809", "283191", "주택법", status, Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", ef, "공포 후 6개월", Law.EnforcementType.유예,
                "심의 절차를 간소화한다.", "주택법 일부를 다음과 같이 개정한다.", addenda, articles, List.of(),
                null, "url", "rev", Instant.now());
    }

    private static CitedSourceResolver resolverWith(CountingLawSource source) {
        source.pending.put("001809@" + EF, law(Law.Status.시행예정, EF, "① 통합심의한다."));
        source.baseline = law(Law.Status.시행중, LocalDate.of(2020, 1, 1), "① 심의한다.");
        return new CitedSourceResolver(source);
    }

    @Test
    @DisplayName("조문 — 법령명 제N조(제목)·본문·현행 원문·변경 유형·시행일, 제목이 라벨과 같으면 괄호 생략")
    void 조문() {
        List<CitedSource> sources = resolverWith(new CountingLawSource()).resolve(List.of(
                "LAW:001809@2026-08-04:art:18", "LAW:001809@2026-08-04:art:19"));

        assertEquals(new CitedSource("LAW:001809@2026-08-04:art:18", "주택법 제18조(사업계획의 통합심의)",
                "① 통합심의한다.", "① 심의한다.", "개정", EF), sources.get(0));
        assertEquals("주택법 제19조", sources.get(1).label());
        assertNull(sources.get(1).previousText(), "무변경 조문은 현행 원문을 싣지 않는다");
    }

    @Test
    @DisplayName("부칙·개정문·개정 이유")
    void 부칙_개정문_개정이유() {
        List<CitedSource> sources = resolverWith(new CountingLawSource()).resolve(List.of(
                "LAW:001809@2026-08-04:addenda:제1조", "LAW:001809@2026-08-04:amend", "LAW:001809@2026-08-04"));

        assertEquals("주택법 부칙 제1조", sources.get(0).label());
        assertEquals("이 법은 공포 후 6개월이 경과한 날부터 시행한다.", sources.get(0).text());
        assertEquals("주택법 개정문", sources.get(1).label());
        assertEquals("주택법 개정 이유", sources.get(2).label());
        assertEquals("심의 절차를 간소화한다.", sources.get(2).text());
    }

    @Test
    @DisplayName("시행일 없는 인용키는 시행중 기준선, 하위 청크(#n)는 조문으로")
    void 기준선_하위청크() {
        List<CitedSource> sources = resolverWith(new CountingLawSource()).resolve(List.of(
                "LAW:001809:art:18", "LAW:001809@2026-08-04:art:18#1"));

        assertEquals(LocalDate.of(2020, 1, 1), sources.get(0).effectiveDate(), "기준선");
        assertNull(sources.get(0).previousText(), "현행본 자체엔 현행 원문이 없다");
        assertNull(sources.get(0).changeType(), "시행예정본이 아니면 변경 유형 배지도 없다");
        assertEquals("주택법 제18조(사업계획의 통합심의)", sources.get(1).label());
    }

    @Test
    @DisplayName("형식 불일치·없는 조문·없는 법령 → label=sourceId, text=null")
    void 찾지못함() {
        List<CitedSource> sources = resolverWith(new CountingLawSource()).resolve(List.of(
                "엉뚱한키", "LAW:001809@2026-08-04:art:999", "LAW:999999@2026-08-04:art:1"));

        for (CitedSource source : sources) {
            assertEquals(source.sourceId(), source.label());
            assertNull(source.text());
        }
    }

    @Test
    @DisplayName("중복 인용은 한 번, 같은 버전 조회도 한 번")
    void 중복제거_조회캐시() {
        CountingLawSource source = new CountingLawSource();
        CitedSourceResolver resolver = resolverWith(source);

        List<CitedSource> sources = resolver.resolve(List.of(
                "LAW:001809@2026-08-04:art:18", "LAW:001809@2026-08-04:art:18", "LAW:001809@2026-08-04:art:19"));

        assertEquals(2, sources.size());
        assertEquals(2, source.lookups, "시행예정본 1 + 기준선 1(개정 조문의 현행 원문)");
    }

    @Test
    @DisplayName("시행중 버전을 날짜로 인용해도 현행 원문·변경 유형은 없다 — 시행예정본만 조문 대비")
    void 시행중_버전_날짜_인용() {
        CountingLawSource source = new CountingLawSource();
        LocalDate inForce = LocalDate.of(2020, 1, 1);
        source.pending.put("001809@" + inForce, law(Law.Status.시행중, inForce, "① 심의한다."));
        CitedSourceResolver resolver = resolverWith(source);

        CitedSource cited = resolver.resolve(List.of("LAW:001809@2020-01-01:art:18")).get(0);

        assertEquals("① 심의한다.", cited.text());
        assertNull(cited.previousText());
        assertNull(cited.changeType());
        assertEquals(1, source.lookups, "현행 원문 조회 자체를 하지 않는다");
    }

    @Test
    @DisplayName("과거·철회 버전도 조문 대비 없음")
    void 과거_철회_버전() {
        CountingLawSource source = new CountingLawSource();
        LocalDate past = LocalDate.of(2019, 1, 1);
        LocalDate withdrawn = LocalDate.of(2018, 1, 1);
        source.pending.put("001809@" + past, law(Law.Status.과거, past, "① 옛날."));
        source.pending.put("001809@" + withdrawn, law(Law.Status.철회, withdrawn, "① 철회."));
        CitedSourceResolver resolver = resolverWith(source);

        for (CitedSource cited : resolver.resolve(List.of(
                "LAW:001809@2019-01-01:art:18", "LAW:001809@2018-01-01:art:18"))) {
            assertNull(cited.previousText());
            assertNull(cited.changeType());
        }
    }

    @Test
    @DisplayName("날짜 형식은 맞지만 달력에 없는 키(2026-13-45) → 예외 없이 label=sourceId, text=null")
    void 잘못된_날짜_키() {
        CitedSource cited = resolverWith(new CountingLawSource())
                .resolve(List.of("LAW:001809@2026-13-45:art:18")).get(0);

        assertEquals("LAW:001809@2026-13-45:art:18", cited.label());
        assertNull(cited.text());
        assertNull(cited.changeType());
    }

    @Test
    @DisplayName("시행예정본의 이동·삭제·신설 조문 — 이동은 옛 번호의 현행 원문, 삭제는 현행 원문, 신설은 없음")
    void 시행예정본_이동_삭제_신설() {
        CountingLawSource source = new CountingLawSource();
        LocalDate ef = LocalDate.of(2027, 1, 1);
        source.pending.put("001809@" + ef, law(Law.Status.시행예정, ef, List.of(
                new Article("20", "제20조", "① 옮겨 왔다.", true, Article.ChangeType.이동, "19", null, ef, true, null),
                new Article("18", "제18조", "", true, Article.ChangeType.삭제, null, null, ef, true, null),
                new Article("25", "제25조", "① 새로 생겼다.", true, Article.ChangeType.신설, null, null, ef, true, null))));
        CitedSourceResolver resolver = resolverWith(source);

        List<CitedSource> sources = resolver.resolve(List.of(
                "LAW:001809@2027-01-01:art:20", "LAW:001809@2027-01-01:art:18", "LAW:001809@2027-01-01:art:25"));

        assertEquals("① 그대로다.", sources.get(0).previousText(), "이동 — movedFrom(19)의 현행 원문");
        assertEquals("이동", sources.get(0).changeType());
        assertEquals("① 심의한다.", sources.get(1).previousText(), "삭제 — 현행 원문");
        assertEquals("삭제", sources.get(1).changeType());
        assertNull(sources.get(2).previousText(), "신설 — 현행 원문 없음");
        assertEquals("신설", sources.get(2).changeType());
    }
}
