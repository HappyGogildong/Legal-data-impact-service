package com.lia.core.web;

import java.time.LocalDate;
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
 * 시행예정본의 개정·삭제·이동 조문은 기준선의 현행 원문도 싣는다(조문 대비).
 * 찾지 못하면 label=sourceId, text=null(칩은 보이되 원문 없음). 설계: docs/components/web/AnalysisApi.md
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
        LocalDate effectiveDate = m.group(2) == null ? null : LocalDate.parse(m.group(2));
        Optional<Law> found = loaded.computeIfAbsent(lawId + "@" + effectiveDate,
                key -> effectiveDate == null ? laws.findBaseline(lawId) : laws.find(lawId, effectiveDate));
        if (found.isEmpty()) return unknown(sourceId);
        Law law = found.get();

        if ("art".equals(m.group(3))) {
            Article article = law.article(m.group(4));
            if (article == null) return unknown(sourceId);
            String label = law.title() + " " + article.label() + titleSuffix(article);
            String changeType = article.changeType() == null ? null : article.changeType().name();
            String previousText = effectiveDate == null ? null : previousText(lawId, article, loaded);
            return new CitedSource(sourceId, label, article.text(), previousText, changeType, law.effectiveDate());
        }
        if ("addenda".equals(m.group(3))) {
            String no = m.group(4);
            return law.addenda().stream().filter(a -> no.equals(a.no())).findFirst()
                    .map(a -> new CitedSource(sourceId, law.title() + " 부칙 " + a.no(), a.text(), null, null, law.effectiveDate()))
                    .orElse(unknown(sourceId));
        }
        if (m.group(5) != null) {
            return new CitedSource(sourceId, law.title() + " 개정문", law.amendText(), null, null, law.effectiveDate());
        }
        return new CitedSource(sourceId, law.title() + " 개정 이유", law.amendReason(), null, null, law.effectiveDate());
    }

    /** 시행예정본 조문의 현행 원문 — 개정·삭제·이동만(신설·무변경은 null). 이동은 옛 번호로 찾는다. */
    private String previousText(String lawId, Article article, Map<String, Optional<Law>> loaded) {
        Article.ChangeType type = article.changeType();
        if (type != Article.ChangeType.개정 && type != Article.ChangeType.삭제 && type != Article.ChangeType.이동) {
            return null;
        }
        String baseNo = type == Article.ChangeType.이동 && article.movedFrom() != null ? article.movedFrom() : article.no();
        // 키는 시행일 없는 인용키와 같은 "lawId@null" — 기준선 조회를 공유한다
        return loaded.computeIfAbsent(lawId + "@" + null, key -> laws.findBaseline(lawId))
                .map(baseline -> baseline.article(baseNo))
                .map(Article::text)
                .orElse(null);
    }

    /** 조문 제목이 있고 라벨과 다르면 "(제목)". 일부 원천은 제목 자리에 "제N조"를 넣어 둔다. */
    private static String titleSuffix(Article article) {
        String title = article.title();
        if (title == null || title.isBlank() || title.equals(article.label())) return "";
        return "(" + title + ")";
    }

    private static CitedSource unknown(String sourceId) {
        return new CitedSource(sourceId, sourceId, null, null, null, null);
    }
}
