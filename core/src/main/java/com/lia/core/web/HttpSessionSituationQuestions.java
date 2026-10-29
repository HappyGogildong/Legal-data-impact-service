package com.lia.core.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lia.core.application.analysis.SituationQuestions;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.pipeline.plan.LawRef;
import jakarta.servlet.http.HttpSession;

/**
 * 상황 질문을 HTTP 세션(서버 메모리, 단일 인스턴스 D63)에 둔다(D71) — DB 저장 아님, 로그아웃·만료 시 사라진다.
 * 세션 속성 하나에 법령 버전 → 질문 목록, 최근 {@value #MAX_LAWS}개만(오래 안 쓴 것부터 지운다). 답은 두지 않는다.
 */
public class HttpSessionSituationQuestions implements SituationQuestions {

    static final String ATTRIBUTE = "LIA_SITUATION_QUESTIONS";
    static final int MAX_LAWS = 20;

    private final HttpSession session;

    public HttpSessionSituationQuestions(HttpSession session) {
        this.session = session;
    }

    @Override
    public Optional<List<SituationQuestion>> find(LawRef ref) {
        synchronized (session) {
            return Optional.ofNullable(questionsByLaw().get(key(ref)));
        }
    }

    @Override
    public void save(LawRef ref, List<SituationQuestion> questions) {
        synchronized (session) {
            questionsByLaw().put(key(ref), List.copyOf(questions));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<SituationQuestion>> questionsByLaw() {
        Object existing = session.getAttribute(ATTRIBUTE);
        if (existing instanceof Map<?, ?> map) return (Map<String, List<SituationQuestion>>) map;
        // accessOrder=true — get 도 사용으로 쳐서 가장 오래 안 쓴 법령부터 밀려난다
        Map<String, List<SituationQuestion>> created = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<SituationQuestion>> eldest) {
                return size() > MAX_LAWS;
            }
        };
        session.setAttribute(ATTRIBUTE, created);
        return created;
    }

    /** 법령 버전 단위 — 조문 번호는 키에 넣지 않는다(조문을 지정한 분석과 지정 없는 재생성이 같은 질문을 본다). */
    static String key(LawRef ref) {
        return ref.lawId() + "@" + ref.effectiveDate();
    }
}
