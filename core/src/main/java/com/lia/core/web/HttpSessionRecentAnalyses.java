package com.lia.core.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.lia.core.pipeline.plan.LawRef;

import jakarta.servlet.http.HttpSession;

/**
 * 최근 분석 결과를 HTTP 세션(서버 메모리, 단일 인스턴스 D63)에 잠시 둔다(D73) — 이력 저장이 화면이 아닌 서버 결과로 스냅샷을 만들게.
 * 최근 {@value #MAX_ANALYSES}개만(오래 안 쓴 것부터 지운다), DB 저장 아님. web DTO 를 보관하므로 web 계층 전용.
 */
public class HttpSessionRecentAnalyses {

    static final String ATTRIBUTE = "LIA_RECENT_ANALYSES";
    static final int MAX_ANALYSES = 20;

    /** 재생성에 실제로 쓴 상황 답 — 질문 문장과 짝지어 둔다(질문 id 는 다음 분석에서 다시 쓰인다). */
    public record AnsweredQuestion(String question, String answer) {}

    /** @param revision 대응 방안 재생성본 — 없으면 null */
    public record Entry(String query, LawRef lawRef, AnalysisApiResponse body, ActionRevisionApiResponse revision,
                        List<AnsweredQuestion> answers) {}

    private final HttpSession session;

    public HttpSessionRecentAnalyses(HttpSession session) {
        this.session = session;
    }

    /** 새 analysisId 로 보관하고 그 id 를 돌려준다. */
    public String remember(String query, LawRef lawRef, AnalysisApiResponse body) {
        String analysisId = UUID.randomUUID().toString();
        synchronized (session) {
            entries().put(analysisId, new Entry(query, lawRef, body, null, List.of()));
        }
        return analysisId;
    }

    public Optional<Entry> find(String analysisId) {
        synchronized (session) {
            return Optional.ofNullable(entries().get(analysisId));
        }
    }

    /** 같은 법령 버전(조문 무시)일 때만 재생성본·답을 덮어 쓴다. 없거나 다르면 그대로. */
    public void attachRevision(String analysisId, LawRef lawRef, ActionRevisionApiResponse revision,
                               List<AnsweredQuestion> answers) {
        synchronized (session) {
            Entry entry = entries().get(analysisId);
            if (entry == null || !sameLawVersion(entry.lawRef(), lawRef)) return;
            entries().put(analysisId, new Entry(entry.query(), entry.lawRef(), entry.body(), revision, List.copyOf(answers)));
        }
    }

    private static boolean sameLawVersion(LawRef a, LawRef b) {
        return a.lawId().equals(b.lawId()) && a.effectiveDate().equals(b.effectiveDate());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Entry> entries() {
        Object existing = session.getAttribute(ATTRIBUTE);
        if (existing instanceof Map<?, ?> map) return (Map<String, Entry>) map;
        Map<String, Entry> created = new RecentEntries();
        session.setAttribute(ATTRIBUTE, created);
        return created;
    }

    /** 접근 순 LRU — 중첩(static) 클래스라 어댑터·세션을 붙잡지 않는다({@link HttpSessionSituationQuestions} 와 같은 이유). */
    private static final class RecentEntries extends LinkedHashMap<String, Entry> {

        private static final long serialVersionUID = 1L;

        RecentEntries() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ANALYSES;
        }
    }
}
