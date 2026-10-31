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

    /**
     * @param userId   이 항목을 만든 로그인 사용자 — 로그아웃 없이 같은 브라우저에서 다른 계정으로 로그인해도(세션 속성이 남는다)
     *                 다른 사용자의 항목은 없는 것처럼 다룬다
     * @param revision 대응 방안 재생성본 — 없으면 null
     */
    public record Entry(UUID userId, String query, LawRef lawRef, AnalysisApiResponse body,
                        ActionRevisionApiResponse revision, List<AnsweredQuestion> answers) {}

    private final HttpSession session;

    public HttpSessionRecentAnalyses(HttpSession session) {
        this.session = session;
    }

    /** 새 analysisId 로 보관하고 그 id 를 돌려준다. */
    public String remember(UUID userId, String query, LawRef lawRef, AnalysisApiResponse body) {
        String analysisId = UUID.randomUUID().toString();
        synchronized (session) {
            entries().put(analysisId, new Entry(userId, query, lawRef, body, null, List.of()));
        }
        return analysisId;
    }

    /** 그 사용자의 항목만 — 다른 사용자의 것이면 없는 것처럼. */
    public Optional<Entry> find(String analysisId, UUID userId) {
        synchronized (session) {
            return Optional.ofNullable(entries().get(analysisId)).filter(entry -> entry.userId().equals(userId));
        }
    }

    /**
     * 재생성본·답을 덮어 쓴다 — 그 사용자의 항목이고, 같은 법령 버전(조문 무시)이며, 요청의 질문 묶음({@code setId})이
     * 그 분석 응답의 것과 같을 때만. 아니면 그대로(재생성은 이미 성공했으니 오류는 아니다).
     */
    public void attachRevision(String analysisId, UUID userId, LawRef lawRef, String setId,
                               ActionRevisionApiResponse revision, List<AnsweredQuestion> answers) {
        synchronized (session) {
            Entry entry = entries().get(analysisId);
            if (entry == null || !entry.userId().equals(userId) || !sameLawVersion(entry.lawRef(), lawRef)
                    || !sameQuestionSet(entry.body(), setId)) return;
            entries().put(analysisId, new Entry(entry.userId(), entry.query(), entry.lawRef(), entry.body(), revision,
                    List.copyOf(answers)));
        }
    }

    private static boolean sameQuestionSet(AnalysisApiResponse body, String setId) {
        return setId != null && body.situation() != null && setId.equals(body.situation().setId());
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
