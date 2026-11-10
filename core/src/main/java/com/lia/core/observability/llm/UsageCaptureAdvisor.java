package com.lia.core.observability.llm;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

/**
 * 응답의 사용량을 요청 컨텍스트의 {@link UsageCapture} 에 담는 advisor. 응답은 바꾸지 않는다.
 * 컨텍스트에 그릇이 없으면 아무것도 하지 않는다.
 */
public class UsageCaptureAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(request);
    }

    @Override
    public String getName() {
        return "UsageCaptureAdvisor";
    }

    /** thinking 블록 필터(LOWEST_PRECEDENCE - 1)보다 바깥 — 걸러진 본문의 종료 사유를 본다(메타데이터는 필터가 유지). */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 2;
    }
}
