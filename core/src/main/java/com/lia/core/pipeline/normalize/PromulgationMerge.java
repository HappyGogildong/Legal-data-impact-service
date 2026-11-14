package com.lia.core.pipeline.normalize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.lia.core.domain.law.Addendum;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.MstList;

/**
 * 같은 (법령ID, 시행일)의 공포본 병합(D85) — 순수 함수. 규칙: docs/components/normalize/Normalizer.md {@code normalizeAll}.
 *
 * <p>본문은 공포본마다 같고 변경 표시·개정문·제개정이유·부칙만 다르다(실측 48/48). 변경 표시가 붙는 공포본은
 * 공포일·공포번호·MST 어느 순서와도 일관되지 않아 하나를 고르지 않고 합친다.
 */
final class PromulgationMerge {

    /** 공포 순서 — (공포일, 공포번호 숫자) 오름차순. 같으면 MST 숫자 순(결정성). */
    static final Comparator<Law> ORDER = Comparator
            .comparing(Law::promulgateDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(law -> number(law.promulgateNo()))
            .thenComparing(law -> number(law.mst()));

    private PromulgationMerge() {}

    /** @param promulgations 정규화된 공포본 2개 이상 — 순서는 상관없다. revision 은 호출자가 다시 계산한다 */
    static Law merge(List<Law> promulgations) {
        List<Law> ordered = promulgations.stream().sorted(ORDER).toList();
        Law first = ordered.getFirst();
        Law last = ordered.getLast();
        for (Law law : ordered) {
            if (!law.lawId().equals(first.lawId()) || !law.effectiveDate().equals(first.effectiveDate())) {
                throw new IllegalArgumentException("다른 버전의 공포본은 병합하지 않는다: " + first.lawId() + "@"
                        + first.effectiveDate() + " ≠ " + law.lawId() + "@" + law.effectiveDate());
            }
        }
        String mst = MstList.join(ordered.stream().map(Law::mst).toList());
        List<String> delegations = new ArrayList<>(ordered.stream()
                .flatMap(law -> law.delegationClauses().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return new Law(last.lawId(), mst, last.title(), last.status(), last.amendKind(), last.lawType(), last.ministry(),
                last.promulgateDate(), last.promulgateNo(), last.effectiveDate(), last.effectiveRule(),
                last.enforcementType(),
                joinWithHeaders(ordered, Law::amendReason),
                joinWithHeaders(ordered, Law::amendText),
                renumberedAddenda(ordered),
                mergedArticles(ordered),
                delegations,
                last.baselineLawId(), last.sourceUrl(), "", last.lastSeen());
    }

    /** 조문은 같아야 한다 — 조문마다 변경 표시가 있는 첫 공포본의 것을 쓴다(변경 표시 합집합). */
    private static List<Article> mergedArticles(List<Law> ordered) {
        Law first = ordered.getFirst();
        int size = first.articles().size();
        for (Law law : ordered) {
            if (law.articles().size() != size) {
                throw mismatch(first, law, "조문 수 " + size + " ≠ " + law.articles().size());
            }
        }
        List<Article> merged = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Article base = first.articles().get(i);
            Article chosen = base;
            for (Law law : ordered) {
                Article candidate = law.articles().get(i);
                if (!sameContent(base, candidate)) {
                    throw mismatch(first, law, "조문 " + base.no() + " 본문이 다르다");
                }
                if (!chosen.changed() && candidate.changed()) {
                    chosen = candidate;
                }
            }
            merged.add(chosen);
        }
        return merged;
    }

    private static boolean sameContent(Article a, Article b) {
        return Objects.equals(a.no(), b.no()) && Objects.equals(a.title(), b.title())
                && Objects.equals(a.text(), b.text()) && a.isArticle() == b.isArticle();
    }

    private static IllegalArgumentException mismatch(Law first, Law other, String detail) {
        return new IllegalArgumentException("같은 시행일 공포본의 조문이 다르다 — 추측해 고르지 않는다: 법령ID "
                + first.lawId() + "@" + first.effectiveDate() + " MST " + first.mst() + " vs " + other.mst()
                + " (" + detail + ")");
    }

    /** 공포 순으로 {@code [yyyy-MM-dd 공포 제N호 구분]} 머리말을 붙여 빈 줄로 잇는다. 빈 것은 건너뛴다. */
    private static String joinWithHeaders(List<Law> ordered, Function<Law, String> field) {
        List<String> parts = new ArrayList<>();
        for (Law law : ordered) {
            String value = field.apply(law);
            if (value == null || value.isBlank()) continue;
            parts.add("[" + law.promulgateDate() + " 공포 제" + law.promulgateNo() + "호 " + law.amendKind() + "]\n"
                    + value.strip());
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    /** 공포본마다 {@code 제1조}가 있어 인용키가 겹친다 — 번호에 공포번호를 붙인다. */
    private static List<Addendum> renumberedAddenda(List<Law> ordered) {
        List<Addendum> out = new ArrayList<>();
        for (Law law : ordered) {
            for (Addendum addendum : law.addenda()) {
                String promulgateNo = addendum.promulgateNo() != null ? addendum.promulgateNo() : law.promulgateNo();
                out.add(new Addendum("제" + promulgateNo + "호-" + addendum.no(), addendum.title(), addendum.kind(),
                        addendum.text(), addendum.promulgateNo(), addendum.promulgateDate()));
            }
        }
        return out;
    }

    /** 숫자 문자열 → 값. 숫자가 아니거나 없으면 맨 뒤. */
    private static long number(String value) {
        if (value == null) return Long.MAX_VALUE;
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
