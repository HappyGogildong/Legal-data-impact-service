package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;
import org.junit.jupiter.api.Test;

class PersonalizedReplyTest {

    @Test
    void 대응의_situationNote_는_단계_설명으로_질문은_임시_id_로_나눈다() {
        PersonalizedReply reply = new PersonalizedReply("LAW:x", "IMPACT", "요약",
                List.of(new ImpactResult.Claim("주장", List.of("A"), 0.9)),
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of("A"))),
                List.of(new PersonalizedReply.NotedAction("만료일 확인", null, List.of("A"), null),
                        new PersonalizedReply.NotedAction("갱신 요구", "만료 6~2개월 전", List.of("A"), "시행일 이전 계약이라 이전 기준")),
                null, List.of("불확실"), "참고",
                List.of(new PersonalizedReply.QuestionDraft("이전에 맺었나요?", "A", SituationQuestion.AnswerType.YES_NO, null)));

        PersonalizedOutput out = reply.toOutput();

        assertEquals(List.of(new ImpactResult.Action("만료일 확인", null, List.of("A")),
                new ImpactResult.Action("갱신 요구", "만료 6~2개월 전", List.of("A"))), out.result().actions());
        assertEquals(List.of(new StepNote(1, "시행일 이전 계약이라 이전 기준")), out.stepNotes());
        assertEquals("q1", out.questions().get(0).id());
        assertEquals(List.of(), out.questions().get(0).options());
        assertNull(out.result().meta(), "meta 는 SpringAiReasoner 가 찍는다");
    }

    @Test
    void 목록이_null_이어도_빈_목록으로() {
        PersonalizedOutput out = new PersonalizedReply(null, null, null, null, null, null, null, null, null, null).toOutput();
        assertTrue(out.questions().isEmpty());
        assertTrue(out.stepNotes().isEmpty());
        assertNull(out.result().actions());
    }

    @Test
    void 모델_출력의_null_항목은_예외_없이_버린다() {
        PersonalizedReply reply = new PersonalizedReply("LAW:x", "ACTION", "요약", null, null,
                Arrays.asList(null, new PersonalizedReply.NotedAction("확인", null, List.of("A"), "이유"), null),
                null, null, null,
                Arrays.asList(
                        null,
                        new PersonalizedReply.QuestionDraft("선택지 질문", "A", SituationQuestion.AnswerType.CHOICE,
                                Arrays.asList("가", null, "나")),
                        new PersonalizedReply.QuestionDraft("두번째", "A", SituationQuestion.AnswerType.YES_NO, null)));

        PersonalizedOutput out = assertDoesNotThrow(reply::toOutput);

        assertEquals(1, out.result().actions().size());
        assertEquals(List.of(new StepNote(0, "이유")), out.stepNotes(), "null 단계를 건너뛴 뒤의 위치");
        assertEquals(2, out.questions().size());
        assertEquals(List.of("가", "나"), out.questions().get(0).options(), "options 의 null 은 버린다");
        assertEquals("q2", out.questions().get(1).id(), "null 초안은 id 번호를 쓰지 않는다");
    }
}
