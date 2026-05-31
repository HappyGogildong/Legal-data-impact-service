package com.lia.core.command;

import com.lia.core.client.PipelineClient;
import com.lia.core.domain.Bill;

/**
 * 커맨드 실행에 필요한 모든 것: 대상 법안, 사용자 페르소나,
 * 해석 엔진(파이프라인) 핸들.
 */
public class CommandContext {
    private final Bill bill;
    private final String persona;          // 일반 시민 페르소나 (nullable)
    private final PipelineClient pipeline; // 파이썬 해석 엔진 호출

    public CommandContext(Bill bill, String persona, PipelineClient pipeline) {
        this.bill = bill;
        this.persona = persona;
        this.pipeline = pipeline;
    }

    public Bill bill() { return bill; }
    public String persona() { return persona; }
    public boolean hasPersona() { return persona != null && !persona.isBlank(); }
    public PipelineClient pipeline() { return pipeline; }
}
