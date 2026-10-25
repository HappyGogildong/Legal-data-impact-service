package com.lia.core.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.store.LawSource;
import com.lia.core.web.AnalysisApiResponse.CitedSource;

/**
 * 인용키(source_id) → 화면용 근거(라벨·원문) — 결과 화면의 인용 칩·근거 패널(screen-plan S02).
 * 형식 {@code LAW:{lawId}[@{efYd}][:art:{no} | :addenda:{no} | :amend][#{n}]} — 시행일이 없으면 시행중 기준선.
 * 찾은 법령이 시행예정본이면 개정·삭제·이동 조문에 기준선의 현행 원문과 변경 유형을 싣는다(조문 대비) —
 * 시행중·과거·철회본은 인용키에 날짜가 있어도 조문 대비 없음(변경 유형도 null).
 * 찾지 못하거나 날짜가 달력에 없으면 label=sourceId, text=null(칩은 보이되 원문 없음). 설계: docs/components/web/AnalysisApi.md
 */
@Component
public class CitedSourceResolver {

    private static final Pattern SOURCE_ID = Pattern.compile(
            "^LAW:([^@:#]+)(?:@(\\d{4}-\\d{2}-\\d{2}))?(?::(art|addenda):([^#]+)|:(amend))?(?:#\\d+)?$");

    private final LawSource laws;

    public CitedSourceResolver(LawSource laws) {
        this.laws = laws;
    }

    /** 중복은 한 번만, 입력 순서 유지. 같은 법령 조회는 이 호출 안에서 한 번. */
    public List<CitedSource> resolve(Collection<String> sourceIds) {
        Map<String, Optional<Law>> loaded = new HashMap<>();
        return new LinkedHashSet<>(sourceIds).stream().map(id -> resolveOne(id, loaded)).toList();
    }

    private CitedSource resolveOne(String sourceId, Map<String, Optional<Law>> loaded) {
        Matcher m = SOURCE_ID.matcher(sourceId);
        if (!m.matches()) return unknown(sourceId);
        String lawId = m.group(1);
        LocalDate effectiveDate;
        try {
            effectiveDate = m.group(2) == null ? null : LocalDate.parse(m.group(2));
        } catch (DateTimeParseException e) {
            return unknown(sourceId); // 2026-13-45 처럼 형식만 맞는 키
        }
        Optional<Law> found = loaded.computeIfAbsent(lawId + "@" + effectiveDate,
                key -> effectiveDate == null ? laws.findBaseline(lawId) : laws.find(lawId, effectiveDate));
        if (found.isEmpty()) return unknown(sourceId);
        Law law = found.get();

        if ("art".equals(m.group(3))) {
            Article article = law.article(m.group(4));
            if (article == null) return unknown(sourceId);
            String label = law.title() + " " + article.label() + ArticleComparison.titleSuffix(article);
            boolean pending = law.status() == Law.Status.시행예정;
            String changeType = pending && article.changeType() != null ? article.changeType().name() : null;
            String previousText = pending ? previousText(lawId, article, loaded) : null;
            return new CitedSource(sourceId, label, article.text(), previousText, changeType, law.effectiveDate());
        }
        if ("addenda".equals(m.group(3))) {
            String no = m.group(4);
            return law.addenda().stream().filter(a -> no.equals(a.no())).findFirst()
                    .map(a -> new CitedSource(sourceId, law.title() + " 부칙 " + a.no(), a.text(), null, null, law.effectiveDate()))
                    .orElseGet(() -> unknown(sourceId));
        }
        if (m.group(5) != null) {
            return new CitedSource(sourceId, law.title() + " 개정문", law.amendText(), null, null, law.effectiveDate());
        }
        return new CitedSource(sourceId, law.title() + " 개정 이유", law.amendReason(), null, null, law.effectiveDate());
    }

    /** 시행예정본 조문의 현행 원문 — 개정·삭제·이동만(신설·무변경은 null). 이동은 옛 번호로 찾는다. */
    private String previousText(String lawId, Article article, Map<String, Optional<Law>> loaded) {
        if (!ArticleComparison.comparable(article)) return null;
        // 키는 시행일 없는 인용키와 같은 "lawId@null" — 기준선 조회를 공유한다
        return loaded.computeIfAbsent(lawId + "@" + null, key -> laws.findBaseline(lawId))
                .map(baseline -> ArticleComparison.previousText(article, baseline))
                .orElse(null);
    }

    private static CitedSource unknown(String sourceId) {
        return new CitedSource(sourceId, sourceId, null, null, null, null);
    }
}
