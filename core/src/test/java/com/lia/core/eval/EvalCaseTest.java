package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.eval.EvalCase.Kind;
import com.lia.core.pipeline.plan.QueryType;

/** 정답 세트 1문항의 형식 규칙과 파일 로드(D83). */
class EvalCaseTest {

    @Test
    @DisplayName("답이 있는 문항은 정답 법령이 하나 이상 있어야 한다")
    void 답이_있는_문항은_정답_법령이_필요하다() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvalCase("주택법 바뀌어?", Kind.ANSWERABLE, true, List.of(), List.of(), null, null));
    }

    @Test
    @DisplayName("관련 법령 없음·거부 문항은 정답 법령을 가질 수 없다")
    void 답이_없는_문항은_정답_법령을_가질_수_없다() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvalCase("우주 면허", Kind.NO_MATCH, false, List.of("001809"), List.of(), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EvalCase("점심 뭐 먹지", Kind.REFUSE, false, List.of("001809"), List.of(), null, null));
    }

    @Test
    @DisplayName("질문과 종류는 필수다")
    void 질문과_종류는_필수다() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvalCase(" ", Kind.REFUSE, false, List.of(), List.of(), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EvalCase("질문", null, false, List.of(), List.of(), null, null));
    }

    @Test
    @DisplayName("빠진 목록은 빈 목록으로 읽는다")
    void 빠진_목록은_빈_목록이다() {
        EvalCase c = new EvalCase("우주 면허", Kind.NO_MATCH, false, null, null, null, null);
        assertEquals(List.of(), c.expectedLawIds());
        assertEquals(List.of(), c.acceptableLawIds());
    }

    @Test
    @DisplayName("정답 세트 파일을 읽는다")
    void 정답_세트_파일을_읽는다() {
        List<EvalCase> cases = GoldenSet.cases("eval/sample-gold.json");

        assertEquals(4, cases.size());
        EvalCase first = cases.get(0);
        assertEquals(Kind.ANSWERABLE, first.kind());
        assertTrue(first.lawNamed());
        assertEquals(List.of("001809"), first.expectedLawIds());
        assertEquals("18", first.articleNo());
        assertEquals(QueryType.DIFF, first.expectedDimension());
        assertEquals(List.of("008888"), cases.get(1).acceptableLawIds());
        assertEquals(Kind.REFUSE, cases.get(3).kind());
    }
}
