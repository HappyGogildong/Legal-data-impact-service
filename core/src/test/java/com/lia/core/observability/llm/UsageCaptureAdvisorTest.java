package com.lia.core.observability.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** UsageCaptureAdvisor — ChatClient 를 실제로 지나 사용량을 그릇에 담는다. 실 API 없음(고정 응답 ChatModel). */
class UsageCaptureAdvisorTest {

    record Answer(String value) {}

    private static ChatResponse response(String json, String finishReason, DefaultUsage usage) {
        Generation body = new Generation(AssistantMessage.builder().content(json).build(),
                ChatGenerationMetadata.builder().finishReason(finishReason).build());
        return new ChatResponse(List.of(body),
                ChatResponseMetadata.builder().model("claude-haiku-4-5-20251001").usage(usage).build());
    }

    private static ChatClient client(ChatResponse fixed) {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return fixed;
            }
        };
        return ChatClient.builder(model).defaultAdvisors(new UsageCaptureAdvisor()).build();
    }

    @Test
    void 응답의_모델_토큰_캐시_종료_사유를_담는다() {
        UsageCapture capture = new UsageCapture();
        ChatResponse fixed = response("{\"value\":\"ok\"}", "end_turn", new DefaultUsage(100, 20, 120, null, 30L, 5L));

        Answer answer = client(fixed).prompt().user("질문").advisors(a -> a.param(UsageCapture.KEY, capture))
                .call().entity(Answer.class);

        assertEquals("ok", answer.value(), "응답은 바꾸지 않는다");
        assertEquals(new LlmUsage("claude-haiku-4-5-20251001", 100, 20, 30, 5, "end_turn"), capture.usage());
    }

    @Test
    void 변환이_실패해도_사용량은_남는다() {
        UsageCapture capture = new UsageCapture();
        ChatResponse fixed = response("{\"value\":", "max_tokens", new DefaultUsage(100, 4096));

        assertThrows(RuntimeException.class, () -> client(fixed).prompt().user("질문")
                .advisors(a -> a.param(UsageCapture.KEY, capture)).call().entity(Answer.class));

        assertEquals(4096, capture.usage().outputTokens());
        assertEquals("max_tokens", capture.usage().stopReason(), "출력 잘림의 근거");
    }

    @Test
    void 그릇이_없으면_아무것도_하지_않는다() {
        ChatResponse fixed = response("{\"value\":\"ok\"}", "end_turn", new DefaultUsage(1, 1));

        assertEquals("ok", client(fixed).prompt().user("질문").call().entity(Answer.class).value());
    }

    @Test
    void 공급자가_주지_않은_값은_비운다() {
        ChatResponse bare = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("x").build())));

        assertEquals(LlmUsage.NONE.inputTokens(), LlmUsage.of(bare).inputTokens());
        assertNull(LlmUsage.of(bare).cacheReadTokens());
        assertNull(LlmUsage.of(bare).stopReason());
    }
}
