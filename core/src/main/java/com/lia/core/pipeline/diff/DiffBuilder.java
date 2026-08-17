package com.lia.core.pipeline.diff;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.observability.Obs;

/**
 * 시행예정본의 <b>변경 조문</b>을 시행중본(기준선)과 대조해 {@code diffVsCurrent} 를 채우고
 * {@code changeType} 을 <b>신설·삭제까지 확정</b>한다.
 *
 * <p><b>왜 여기서 신설·삭제를 정하나.</b> Normalizer 는 기준선 없이 한 버전만 보므로
 * {@code 개정}/{@code 이동}/{@code 없음}까지만 판정한다({@link Article} 주석). 신설(현행에 없음)과
 * 삭제(현행에만 있음)는 <b>같은 조문번호를 두 버전에서 대조</b>해야 비로소 확정된다.
 *
 * <p><b>정렬은 조문번호가 곧 정렬키다</b>(D42). 시행중본과 시행예정본이 동일 스키마로
 * 조문 전문을 주므로, 삭제는 시행예정본에 <b>"삭제" 마커 조문</b>으로 이미 들어온다 —
 * 별도의 팬텀 스캔 없이 in-place 로 감지한다("플래그가 정답" 규율, §1.2).
 *
 * <p><b>대상은 {@code changed=true} 조문뿐이다</b>(비용 레버 — 실측 137→6). 나머지는 손대지 않는다.
 *
 * <p>기준선이 없으면(제정 — 현행본 부재) 변경 조문은 모두 <b>신설</b>이다.
 *
 * <p>설계: docs/components/component-specs.md §1.2·§4(#17), D42·D43
 */
public class DiffBuilder {

    /** 계측 레지스트리 — 미주입 시 NOOP(단위 테스트의 {@code new DiffBuilder()} 무영향). */
    private final ObservationRegistry observations;

    public DiffBuilder() {
        this(ObservationRegistry.NOOP);
    }

    public DiffBuilder(ObservationRegistry observations) {
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
    }

    /**
     * @param pending  시행예정본 (필수). 조문에 {@code changed} 플래그가 채워져 있어야 한다.
     * @param baseline 같은 {@code lawId} 의 시행중본. 없으면(제정) {@code null}.
     * @return 변경 조문의 {@code diffVsCurrent}·확정 {@code changeType} 이 반영된 시행예정본 사본.
     */
    public Law build(Law pending, Law baseline) {
        if (pending == null) {
            throw new IllegalArgumentException("시행예정본은 필수다 — 대조 대상.");
        }
        return Observation.createNotStarted(Obs.DIFF, observations).observe(() -> {
            List<Article> out = new ArrayList<>(pending.articles().size());
            for (Article a : pending.articles()) {
                out.add(a.changed() ? diffed(a, baseline) : a);
            }
            return pending.withArticles(out);
        });
    }

    /** 변경 조문 하나를 기준선과 대조해 확정 타입·대조문을 채운다. */
    private Article diffed(Article a, Law baseline) {
        // 이동은 옛 번호로 기준선을 찾는다; 그 외는 같은 번호.
        boolean moved = a.changeType() == ChangeType.이동 && notBlank(a.movedFrom());
        String baseNo = moved ? a.movedFrom() : a.no();
        Article base = (baseline == null || baseNo == null) ? null : baseline.article(baseNo);

        if (isDeletionMarker(a)) {
            return a.withDiff(ChangeType.삭제, renderDeleted(base));
        }
        if (moved) {
            return a.withDiff(ChangeType.이동, renderMoved(a, base));
        }
        if (base == null) {                      // 기준선에 대응 조문 없음 = 신설
            return a.withDiff(ChangeType.신설, renderNew(a));
        }
        return a.withDiff(ChangeType.개정, renderAmended(a, base));   // 양쪽에 존재 = 개정
    }

    // --- 대조문 렌더 (LLM·사용자가 읽는 근거 텍스트) ----------------------------

    private String renderNew(Article a) {
        return "[신설] 현행 없음\n개정: " + text(a);
    }

    private String renderDeleted(Article base) {
        return "[삭제] 현행: " + (base == null ? "(현행본 미확보)" : text(base)) + "\n→ 삭제";
    }

    private String renderMoved(Article a, Article base) {
        String head = "[이동] 제" + orQ(a.movedFrom()) + "조 → " + a.label();
        if (base == null) {
            return head;
        }
        if (equalText(a, base)) {
            return head + " (자구 동일)";
        }
        return head + "\n현행: " + text(base) + "\n개정: " + text(a);
    }

    private String renderAmended(Article a, Article base) {
        if (equalText(a, base)) {
            return "[개정] (자구 동일 — 플래그 기준 변경)\n" + text(a);
        }
        return "[개정]\n현행: " + text(base) + "\n개정: " + text(a);
    }

    // --- 판정 헬퍼 ---------------------------------------------------------

    /**
     * "삭제" 마커 조문인가. 실측 표기: {@code "제104조(벌칙) 삭제 <2026. 2. 3.>"}.
     * 조문 헤더 {@code 제N조(제목)} 와 개정일 주석 {@code <...>} 을 걷어낸 본문이 "삭제"뿐이면 삭제다.
     */
    private boolean isDeletionMarker(Article a) {
        String t = a.text();
        if (t == null) {
            return false;
        }
        String body = t.replaceAll("제\\d+조(?:의\\d+)?\\s*(?:\\([^)]*\\))?", "")  // 조문 헤더 제거
                       .replaceAll("<[^>]*>", "")                                 // 개정일 주석 제거
                       .trim();
        return body.equals("삭제");
    }

    private boolean equalText(Article x, Article y) {
        return norm(text(x)).equals(norm(text(y)));
    }

    private static String text(Article a) {
        return a == null || a.text() == null ? "" : a.text();
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String orQ(String s) {
        return notBlank(s) ? s : "?";
    }
}
