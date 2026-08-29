package com.lia.core.pipeline.plan;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * {@link QueryTranslator}의 Spring AI 구현 — 자연어를 {@link AnalysisQueryDraft}로 번역(D46).
 *
 * <p>모델 <b>Haiku 4.5</b>(추출·분류는 저비용 티어링; 실제 분석 생성은 Opus, component-specs §3.3).
 * 구조화 출력은 `.entity(AnalysisQueryDraft.class)` — BeanOutputConverter가 스키마를 생성해 강제한다.
 * 이 클래스가 파이프라인의 <b>유일한 LLM 자유도</b>이며, 이후 경로는 결정론이다.
 */
public class SpringAiQueryTranslator implements QueryTranslator {

    /** component-specs §3.3 — 분류·추출 저비용 모델. */
    static final String MODEL = "claude-haiku-4-5-20251001";

    private static final String SYSTEM = """
            너는 한국 법령 서비스의 질의 분석기다. 사용자의 자연어 질의를 구조화된 초안으로 번역한다.
            판단만 하고 답을 지어내지 않는다.

            - isLawQuery: 법령/제도/규제에 관한 질의면 true, 아니면(잡담·오프토픽) false.
            - targetKind: 특정 법령을 지목하면 REFERENCE, "나에게 영향 있을 법 찾아줘"처럼 탐색이면 DISCOVERY.
            - primaryType 과 types(집합): LOOKUP(발견)·SUMMARY(요약)·DIFF(변경점)·IMPACT(내 영향)·ACTION(대응).
              포괄 질의는 여러 개. 애매하면 REFERENCE는 SUMMARY, DISCOVERY는 LOOKUP을 기본으로.
            - REFERENCE면 lawName(과 있으면 articleNo)을 뽑는다. DISCOVERY면 keywords·conditions·domains를 뽑는다.
            - intentSummary: 사용자가 알고 싶은 것 한 줄.
            해소·검색은 하지 않는다 — 오직 질의를 분류·추출한다.
            """;

    private final ChatClient chat;

    /**
     * {@code ChatModel}을 명시 주입한다 — openai(임베딩)·anthropic 두 채팅 모델이 공존해
     * 자동설정 {@code ChatClient.Builder}가 모호하므로, 호출부가 Anthropic 모델을 골라 넘긴다.
     */
    public SpringAiQueryTranslator(ChatModel chatModel) {
        this.chat = ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder().model(MODEL))
                .defaultSystem(SYSTEM)
                .build();
    }

    @Override
    public AnalysisQueryDraft translate(String query) {
        return chat.prompt().user(query).call().entity(AnalysisQueryDraft.class);
    }
}
