package com.lia.core.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.lia.core.domain.Bill;
import com.lia.core.domain.ImpactResult;

import java.util.List;

/**
 * 파이썬 해석 파이프라인 호출 클라이언트.
 * 코어(Java)는 오케스트레이션, 실제 수집/NLP/LLM 추론은 파이프라인(Python)이 담당.
 */
@Component
public class PipelineClient {

    private final WebClient http;

    public PipelineClient(@Value("${pipeline.base-url:http://localhost:8000}") String baseUrl) {
        this.http = WebClient.builder().baseUrl(baseUrl).build();
    }

    public ImpactResult summarize(Bill bill, String persona) {
        // TODO: POST /analyze/summarize {billId, persona} → ImpactResult
        return placeholder(bill, persona, "요약");
    }

    public ImpactResult actionPlan(Bill bill, String persona) {
        // TODO: POST /analyze/action-plan
        return placeholder(bill, persona, "행동계획");
    }

    private ImpactResult placeholder(Bill bill, String persona, String kind) {
        return new ImpactResult(
                bill.id(), persona,
                "[PoC] " + bill.title() + " " + kind + " (파이프라인 연결 전)",
                List.of(), List.of(), List.of(), 0.0);
    }
}
