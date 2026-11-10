package com.lia.core.observability.llm;

/**
 * 채팅 호출 1회의 사용량을 받아 두는 그릇 — 호출자가 만들어 advisor 파라미터({@link #KEY})로 넘기면
 * {@link UsageCaptureAdvisor} 가 응답의 사용량을 담는다. {@code .entity()} 변환 전에 담기므로 변환이 실패해도 남는다.
 * 호출 1회용이며 스레드 간에 공유하지 않는다.
 */
public final class UsageCapture {

    /** {@code ChatClient...advisors(a -> a.param(KEY, capture))} 의 키. */
    public static final String KEY = "lia.usageCapture";

    private LlmUsage usage = LlmUsage.NONE;

    public void capture(LlmUsage captured) {
        this.usage = captured == null ? LlmUsage.NONE : captured;
    }

    /** 받은 사용량. 응답이 없었으면 {@link LlmUsage#NONE}. */
    public LlmUsage usage() {
        return usage;
    }
}
