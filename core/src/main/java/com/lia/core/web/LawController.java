package com.lia.core.web;

import java.time.DateTimeException;
import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.store.LawCatalogStore;
import com.lia.core.store.LawSource;
import com.lia.core.store.LawSummaries;

/**
 * {@code /api/v1/laws} — 곧 시행되는 법령 목록·시행예정본 목록·법령 상세([[service-api-spec]] §3.1~3.3, D65).
 * <b>HTTP 경계만</b>: 파라미터 검증(400 — {@link IllegalArgumentException})·없음(404 — {@link LawNotFoundException}).
 * 익명 공개({@code SecurityConfig}의 {@code /api/v1/laws/**}). 설계: docs/components/web/LawCatalogApi.md
 */
@RestController
@RequestMapping("/api/v1/laws")
public class LawController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final LawCatalogStore catalog;
    private final LawSource laws;
    private final LawSummaries summaries;
    private final LawDetailMapper mapper;

    public LawController(LawCatalogStore catalog, LawSource laws, LawSummaries summaries, LawDetailMapper mapper) {
        this.catalog = catalog;
        this.laws = laws;
        this.summaries = summaries;
        this.mapper = mapper;
    }

    @GetMapping
    public LawCatalogStore.LawListPage list(@RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        LocalDate fromDate = date(from, "from");
        LocalDate toDate = date(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from 은 to 보다 늦을 수 없습니다.");
        }
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit 은 1~" + MAX_LIMIT + " 입니다.");
        }
        LawCatalogStore.Cursor after = cursor == null ? null : LawCatalogStore.Cursor.decode(cursor);
        return catalog.listPending(fromDate, toDate, after, size);
    }

    @GetMapping("/{lawId}")
    public LawCatalogStore.LawVersions versions(@PathVariable String lawId) {
        return catalog.versions(lawId).orElseThrow(() -> new LawNotFoundException(lawId));
    }

    @GetMapping("/{lawId}/{effectiveDate}")
    public LawDetailResponse detail(@PathVariable String lawId, @PathVariable String effectiveDate) {
        LocalDate date = date(effectiveDate, "effectiveDate");
        Law law = laws.find(lawId, date).orElseThrow(() -> new LawNotFoundException(lawId + "@" + effectiveDate));
        Law baseline = law.pending() ? laws.findBaseline(lawId).orElse(null) : null;   // 대비는 시행예정본만
        ImpactResult summary = law.pending() ? summaries.findCurrent(law).orElse(null) : null;   // D67
        return mapper.toBody(law, baseline, summary);
    }

    /** "YYYY-MM-DD"(달력에 있는 날)만. null 은 조건 없음. */
    private static LocalDate date(String value, String name) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(name + " 은 YYYY-MM-DD 형식이어야 합니다.");
        }
    }
}
