package com.lia.core.pipeline.analyze;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalysisContext.SourceBlock;
import com.lia.core.pipeline.plan.QueryType;

/**
 * {@link Reasoner}의 Spring AI 구현 — 조립된 context → Opus 4.8 → 구조화 {@link ImpactResult}(§3·§4).
 *
 * <p>시스템 프롬프트가 그라운딩 가드레일(§3 SYSTEM)을 고정하고, 구조화 출력 `.entity(ImpactResult.class)`로
 * 스키마를 강제한다. 인용 존재성 <b>검증·재생성은 상위 {@link AnalysisEngine}</b>이 결정론으로 한다.
 * {@code ChatModel}을 명시 주입(openai/anthropic 공존 모호 해소).
 */
public class SpringAiReasoner implements Reasoner {

    static final String MODEL = "claude-opus-4-8";
    static final String PROMPT_VERSION = "0.2";   // analysis-prompt-spec 버전

    private static final String SYSTEM = """
            역할: 대한민국 시행 예정 법령 영향 분석가.
            규칙:
             - 제공된 CONTEXT의 source_id 근거만 사용한다. 외부 지식·추측 금지.
             - 모든 claim에 최소 1개 citation(제공된 source_id)을 붙인다. 근거 없으면 그 주장을 쓰지 않는다.
             - impacts의 citations도 제공된 source_id만.
             - 불확실하면 confidence를 낮추고 uncertainties에 명시한다.
             - 법률 자문이 아닌 참고 정보다. disclaimer를 포함한다.
             - lawRef·command·claims를 반드시 채운다. 출력은 지정된 JSON 스키마만.
            """;

    private final ChatClient chat;

    public SpringAiReasoner(ChatModel chatModel) {
        this.chat = ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder().model(MODEL))
                .defaultSystem(SYSTEM)
                .build();
    }

    @Override
    public ImpactResult reason(AnalysisContext context) {
        ImpactResult raw = chat.prompt().user(userMessage(context)).call().entity(ImpactResult.class);
        return stampMeta(raw, context.dimension());   // meta는 LLM이 아니라 우리가 확정(모델명·버전·계층)
    }

    /**
     * {@code meta}를 권위 있는 값으로 덮어쓴다 — LLM은 content만 신뢰하고 메타데이터는 우리가 채운다.
     * (구조화 출력에서 모델이 {@code model:"gpt"} 등 잘못 채우던 것을 교정.)
     */
    static ImpactResult stampMeta(ImpactResult r, QueryType dimension) {
        ImpactResult.Meta meta = new ImpactResult.Meta(MODEL, PROMPT_VERSION, layer(dimension));
        return new ImpactResult(r.lawRef(), r.command(), r.summary(), r.claims(), r.impacts(),
                r.actions(), r.effectiveInfo(), r.uncertainties(), r.disclaimer(), meta);
    }

    /** 차원 → 계층(A: SUMMARY·DIFF 선계산 / B: IMPACT·ACTION 프로필별). */
    private static String layer(QueryType dimension) {
        return dimension.isLayerB() ? "B" : "A";
    }

    /** CONTEXT(source_id별 근거 블록) + TASK(차원) 조립. 프롬프트 §3. */
    private static String userMessage(AnalysisContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("[CONTEXT] law_ref=").append(ctx.lawRef()).append('\n');
        for (SourceBlock b : ctx.blocks()) {
            sb.append('[').append(b.sourceId()).append(" | ").append(b.type()).append("] ")
              .append(b.text()).append('\n');
        }
        sb.append("\n[TASK] 차원=").append(ctx.dimension())
          .append(" — 위 근거만으로 분석해 ImpactResult(JSON)로 답하라. ")
          .append("claims/impacts의 citations는 위 CONTEXT의 source_id만 사용한다.");
        return sb.toString();
    }
}
