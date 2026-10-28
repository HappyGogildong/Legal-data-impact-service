package com.lia.core.pipeline.analyze;

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

/**
 * 응답에서 thinking 블록을 걷어 내는 advisor(D68). Spring AI 2.0 은 Anthropic 의 thinking 블록을 본문과 같은
 * generation 으로 앞에 두고(내용 = thinking 원문, 메타데이터 {@code signature}), {@code .entity()}는 <b>첫</b> generation 만
 * 파싱한다 — Opus 5.5 는 thinking 을 끌 수 없고 기본 내용이 빈 문자열이라 "No content to map" 으로 실패한다.
 * 본문(내용 있음 + signature 없음)만 남긴다. 남는 게 없으면 원래 응답 그대로 — 파싱 실패가 원인을 드러내게 둔다.
 */
class ThinkingBlockFilter implements CallAdvisor {

    private static final String THINKING_SIGNATURE = "signature";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return response;
        }
        List<Generation> body = chatResponse.getResults().stream().filter(ThinkingBlockFilter::isBody).toList();
        if (body.isEmpty() || body.size() == chatResponse.getResults().size()) {
            return response;
        }
        return response.mutate().chatResponse(new ChatResponse(body, chatResponse.getMetadata())).build();
    }

    private static boolean isBody(Generation generation) {
        String text = generation.getOutput().getText();
        return text != null && !text.isBlank() && !generation.getOutput().getMetadata().containsKey(THINKING_SIGNATURE);
    }

    @Override
    public String getName() {
        return "ThinkingBlockFilter";
    }

    /** 가장 바깥(모델에 가장 먼) 쪽이 아니라 모델 바로 앞 — 다른 advisor 가 걸러진 응답을 보게 한다. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
