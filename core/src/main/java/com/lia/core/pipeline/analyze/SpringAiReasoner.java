package com.lia.core.pipeline.analyze;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import com.anthropic.models.messages.OutputConfig;

import java.util.List;

import com.lia.core.domain.analysis.ConfirmedFact;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalysisContext.SourceBlock;
import com.lia.core.pipeline.plan.QueryType;

/**
 * {@link Reasoner}의 Spring AI 구현 — 조립된 context → Opus 5.5 → 구조화 {@link ImpactResult}(§3·§4).
 * adaptive thinking(Opus 5.5 는 끌 수 없다) + effort {@code medium}(D68). thinking 토큰도 {@code max_tokens}에 들어가므로
 * 상한은 {@code spring.ai.anthropic.chat.options.max-tokens}(16000)로 넉넉히 둔다.
 *
 * <p>시스템 프롬프트가 그라운딩 가드레일(§3 SYSTEM)을 고정하고, 구조화 출력 `.entity(ImpactResult.class)`로
 * 스키마를 강제한다. 인용 존재성 <b>검증·재생성은 상위 {@link AnalysisEngine}</b>이 결정론으로 한다.
 * {@code ChatModel}을 명시 주입(openai/anthropic 공존 모호 해소).
 */
public class SpringAiReasoner implements Reasoner {

    public static final String MODEL = "claude-opus-5-5";
    /**
     * analysis-prompt-spec 버전. 올리면 {@code law_summaries}의 저장본이 모두 낡은 것이 된다(유효 = 프롬프트 버전 일치).
     * 적재는 대기 내용이 바뀔 때만 요약을 다시 등록하므로, 올린 뒤에는 운영자가 {@code summarize} 명령을 돌려 다시 채워야 한다
     * (유료 호출 규모는 먼저 {@code summarize --dry-run}으로 확인). 결과 스키마({@code ImpactResult})·모델·추론 설정을 바꿀 때도 같다.
     */
    public static final String PROMPT_VERSION = "0.3";

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
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(MODEL)
                        .thinkingAdaptive()
                        .effort(OutputConfig.Effort.MEDIUM))
                .defaultAdvisors(new ThinkingBlockFilter())   // thinking 블록이 첫 generation 이라 .entity() 가 빈 내용을 파싱하는 것 방지
                .defaultSystem(SYSTEM)
                .build();
    }

    @Override
    public ImpactResult reason(AnalysisContext context) {
        ImpactResult raw = chat.prompt().user(userMessage(context)).call().entity(ImpactResult.class);
        return stampMeta(raw, context.dimension());   // meta는 LLM이 아니라 우리가 확정(모델명·버전·계층)
    }

    @Override
    public PersonalizedOutput reasonPersonalized(AnalysisContext context) {
        PersonalizedReply raw = chat.prompt().user(userMessage(context)).call().entity(PersonalizedReply.class);
        if (raw == null) return new PersonalizedOutput(null, List.of(), List.of());   // 엔진 그라운딩이 실패로 처리
        PersonalizedOutput out = raw.toOutput();
        return new PersonalizedOutput(stampMeta(out.result(), context.dimension()), out.questions(), out.stepNotes());
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

    /** CONTEXT(source_id별 근거 블록) [+ PROFILE·QUESTION — Layer B 만] + TASK(차원) 조립. 프롬프트 §3. */
    // 테스트에서 메시지 구성을 확인한다
    static String userMessage(AnalysisContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("[CONTEXT] law_ref=").append(ctx.lawRef()).append('\n');
        for (SourceBlock b : ctx.blocks()) {
            sb.append('[').append(b.sourceId()).append(" | ").append(b.type()).append("] ")
              .append(b.text()).append('\n');
        }
        if (ctx.dimension() == QueryType.ACTION) {   // 상황 확인 후 대응 방안만 재생성(D71)
            sb.append("\n[PROFILE] (이 사용자의 자기신고 정보 — 근거가 아니므로 인용하지 않는다)\n")
              .append(ctx.profile().labelLines());
            sb.append("\n[SITUATION] (사용자가 확인한 상황 — 근거가 아니므로 인용하지 않는다)\n");
            for (ConfirmedFact fact : ctx.situation()) {
                sb.append("- ").append(fact.question()).append(": ").append(fact.answer()).append('\n');
            }
            sb.append("\n[TASK] 차원=ACTION — 위 근거만으로, PROFILE·SITUATION 의 사용자가 해야 할 일(actions)만 답하라. ")
              .append("actions 의 basis 는 위 CONTEXT 의 source_id 만 사용한다. ")
              .append("SITUATION 이 영향을 준 단계에는 situationNote 에 그 이유를 한 줄로 쓴다. situationQuestions 는 비운다.");
            return sb.toString();
        }
        if (ctx.dimension().isLayerB()) {
            sb.append("\n[PROFILE] (이 사용자의 자기신고 정보 — 근거가 아니므로 인용하지 않는다)\n")
              .append(ctx.profile().labelLines());
            sb.append("\n[QUESTION] ").append(ctx.question() == null ? "" : ctx.question()).append('\n');
            sb.append("\n[TASK] 차원=IMPACT·ACTION — 위 근거만으로, PROFILE 의 사용자에게 이 개정이 미치는 영향(impacts)과 ")
              .append("해야 할 일(actions)을 QUESTION 에 맞춰 ImpactResult(JSON)로 답하라. impacts·actions 를 모두 채운다 — ")
              .append("해당이 없으면 impacts 에 direction \"영향 없음\", actions 에 \"따로 할 일 없음\"을 적용 대상 조문 인용과 함께 쓴다. ")
              .append("claims·impacts 의 citations 와 actions 의 basis 는 위 CONTEXT 의 source_id 만 사용한다.")
              .append(" 사용자 상황에 따라 대응이 갈리는 단계가 있으면 그 상황을 묻는 질문을 situationQuestions 에 최대 3개 쓴다 — ")
              .append("각 질문은 예/아니오(answerType YES_NO)·날짜(DATE)·선택지 2~4개(CHOICE, options) 중 하나로 답할 수 있게, ")
              .append("근거 조문 source_id 를 basis 에, PROFILE 로 이미 알 수 있는 것은 묻지 않는다. 상황이 영향을 준 단계에는 situationNote 를 쓴다.");
            return sb.toString();
        }
        sb.append("\n[TASK] 차원=").append(ctx.dimension())
          .append(" — 위 근거만으로 분석해 ImpactResult(JSON)로 답하라. ")
          .append("claims/impacts의 citations는 위 CONTEXT의 source_id만 사용한다.");
        return sb.toString();
    }
}
