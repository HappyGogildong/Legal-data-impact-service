package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.SituationQuestion.AnswerType;
import com.lia.core.pipeline.plan.LawRef;

/** HttpSessionSituationQuestions 단위 — 법령 버전 단위 보관·덮어쓰기·LRU 20·조문 번호 무시. */
class HttpSessionSituationQuestionsTest {

    static final LocalDate EF = LocalDate.of(2026, 8, 4);

    private final MockHttpSession session = new MockHttpSession();
    private final HttpSessionSituationQuestions store = new HttpSessionSituationQuestions(session);

    private static LawRef ref(String lawId, LocalDate effectiveDate) {
        return new LawRef(lawId, effectiveDate, null);
    }

    private static List<SituationQuestion> questions(String id) {
        return List.of(new SituationQuestion(id, "계약을 이미 했나요?", "LAW:001809@2026-08-04:art:18", AnswerType.YES_NO, List.of()));
    }

    @Test
    @DisplayName("save 한 목록을 같은 법령 버전으로 find 하면 그대로 나온다")
    void 저장하고_찾는다() {
        store.save(ref("001809", EF), questions("q1"));

        assertEquals(Optional.of(questions("q1")), store.find(ref("001809", EF)));
    }

    @Test
    @DisplayName("보관한 적 없는 법령 버전은 비어 있다")
    void 없으면_비어있다() {
        assertTrue(store.find(ref("001809", EF)).isEmpty());
    }

    @Test
    @DisplayName("다른 법령·다른 시행일은 서로 분리된다")
    void 법령_버전별로_분리된다() {
        store.save(ref("001809", EF), questions("q1"));

        assertTrue(store.find(ref("001809", EF.plusDays(1))).isEmpty(), "같은 법령의 다른 시행일");
        assertTrue(store.find(ref("009999", EF)).isEmpty(), "다른 법령");
    }

    @Test
    @DisplayName("같은 법령 버전을 다시 save 하면 덮어쓴다")
    void 다시_저장하면_덮어쓴다() {
        store.save(ref("001809", EF), questions("q1"));
        store.save(ref("001809", EF), questions("q2"));

        assertEquals(Optional.of(questions("q2")), store.find(ref("001809", EF)));
    }

    @Test
    @DisplayName("조문 번호가 달라도 같은 키 — 조문을 지정한 분석에서 저장한 질문을 조문 없는 재생성이 찾는다(역방향도)")
    void 조문번호는_키에_넣지_않는다() {
        store.save(new LawRef("001809", EF, "18"), questions("q1"));
        assertEquals(Optional.of(questions("q1")), store.find(new LawRef("001809", EF, null)));
        assertEquals(Optional.of(questions("q1")), store.find(new LawRef("001809", EF, "46")));

        store.save(new LawRef("009999", EF, null), questions("q2"));
        assertEquals(Optional.of(questions("q2")), store.find(new LawRef("009999", EF, "3")));
    }

    @Test
    @DisplayName("21번째 법령을 save 하면 가장 오래 안 쓴 것이 사라진다")
    void 스물한번째는_가장_오래된_것을_밀어낸다() {
        for (int i = 0; i < HttpSessionSituationQuestions.MAX_LAWS; i++) {
            store.save(ref("law" + i, EF), questions("q" + i));
        }
        store.save(ref("law20", EF), questions("q20"));

        assertTrue(store.find(ref("law0", EF)).isEmpty(), "가장 오래된 것이 밀려난다");
        assertTrue(store.find(ref("law1", EF)).isPresent());
        assertTrue(store.find(ref("law20", EF)).isPresent());
    }

    @Test
    @DisplayName("find 도 사용으로 쳐서 LRU 순서가 갱신된다 - 방금 찾은 것은 남고 그다음으로 오래된 것이 밀려난다")
    void find_는_사용으로_친다() {
        for (int i = 0; i < HttpSessionSituationQuestions.MAX_LAWS; i++) {
            store.save(ref("law" + i, EF), questions("q" + i));
        }
        store.find(ref("law0", EF));
        store.save(ref("law20", EF), questions("q20"));

        assertTrue(store.find(ref("law0", EF)).isPresent(), "방금 쓴 것은 남는다");
        assertTrue(store.find(ref("law1", EF)).isEmpty(), "그다음으로 오래 안 쓴 것이 밀려난다");
    }

    @Test
    @DisplayName("세션 속성 하나(LIA_SITUATION_QUESTIONS)에만 둔다")
    void 세션_속성_하나() {
        store.save(ref("001809", EF), questions("q1"));

        assertEquals("LIA_SITUATION_QUESTIONS", HttpSessionSituationQuestions.ATTRIBUTE);
        assertEquals(List.of(HttpSessionSituationQuestions.ATTRIBUTE), Collections.list(session.getAttributeNames()));
    }
}
