package com.lia.core.pipeline.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.lia.core.observability.llm.LlmCall;
import com.lia.core.observability.llm.LlmPurpose;
import com.lia.core.observability.llm.LlmUsage;

/** SpringAiQueryTranslator 의 호출 기록(D83) — 분류 1회 = CLASSIFY 한 행. 실 API 없음(고정 응답 ChatModel). */
class SpringAiQueryTranslatorTest {

    private static final String DRAFT = """
            {"primaryType":"SUMMARY","types":["SUMMARY"],"isLawQuery":true,"targetKind":"REFERENCE",
             "lawName":"약사법","articleNo":null,"keywords":[],"conditions":[],"domains":[],"intentSummary":"요약"}
            """;

    private final List<LlmCall> calls = new ArrayList<>();

    @Test
    void 분류_한_번에_CLASSIFY_한_행과_토큰() {
        ChatResponse response = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(DRAFT).build(),
                ChatGenerationMetadata.builder().finishReason("end_turn").build())),
                ChatResponseMetadata.builder().model("claude-haiku-4-5-20251001").usage(new DefaultUsage(800, 60)).build());
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response;
            }
        };

        AnalysisQueryDraft draft = new SpringAiQueryTranslator(model, calls::add).translate("약사법 요약해 줘");

        assertEquals("약사법", draft.lawName());
        assertEquals(1, calls.size());
        LlmCall call = calls.get(0);
        assertEquals(LlmPurpose.CLASSIFY, call.purpose());
        assertEquals("claude-haiku-4-5-20251001", call.model());
        assertTrue(call.succeeded());
        assertEquals(new LlmUsage("claude-haiku-4-5-20251001", 800, 60, null, null, "end_turn"), call.usage());
        assertNull(call.groupKey());
    }

    @Test
    void 지시문은_약칭을_정식_법령명으로_풀라고_한다() {   // D88 — 해소는 적재 제목 글자 그대로 검색이라 약칭은 못 찾는다
        List<Prompt> seen = new ArrayList<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                seen.add(prompt);
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(DRAFT).build())));
            }
        };

        new SpringAiQueryTranslator(model, calls::add).translate("공정거래법 바뀐 거 알려줘");

        String system = seen.get(0).getSystemMessage().getText();
        assertTrue(system.contains("정식 법령명"), system);
        assertTrue(system.contains("공정거래법 → 독점규제 및 공정거래에 관한 법률"), "약칭을 푸는 예시");
        assertTrue(system.contains("그대로"), "정식 명칭을 모르면 지어내지 말고 사용자가 쓴 이름을 둔다");
    }

    @Test
    void 호출이_실패하면_실패_행을_남기고_같은_예외를_다시_던진다() {
        RuntimeException boom = new IllegalStateException("529 overloaded");
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw boom;
            }
        };

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new SpringAiQueryTranslator(failing, calls::add).translate("질문"));

        assertSame(boom, thrown);
        assertEquals(1, calls.size());
        assertFalse(calls.get(0).succeeded());
        assertEquals(SpringAiQueryTranslator.MODEL, calls.get(0).model(), "응답이 없으면 요청한 모델");
    }
}
