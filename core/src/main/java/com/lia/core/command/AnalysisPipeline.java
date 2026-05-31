package com.lia.core.command;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 커맨드(또는 커맨드 체인) 실행 오케스트레이터.
 *
 * <p>커맨드가 선언한 {@link Requirement}를 보고 선행 데이터(원문 로드,
 * 현행법 diff 등)를 먼저 채운 뒤 실행한다. 체인 실행도 지원한다
 * (예: SourceResolve → LawDiff → PersonaImpact → ActionPlan).
 */
@Service
public class AnalysisPipeline {

    private final CommandRegistry registry;

    public AnalysisPipeline(CommandRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public <P, R> R run(String commandName, CommandContext ctx, P params) {
        AnalysisCommand<P, R> cmd = (AnalysisCommand<P, R>) registry.find(commandName)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 커맨드: " + commandName));

        if (!cmd.supports(ctx)) {
            throw new IllegalStateException(commandName + " 실행 조건 불충족 (예: 페르소나 필요)");
        }
        resolveRequirements(cmd.requirements(), ctx);
        return cmd.execute(ctx, params);
    }

    /** 커맨드 체인: 앞 커맨드의 부수효과(컨텍스트 보강)를 뒤로 전달. */
    public void runChain(List<String> commandNames, CommandContext ctx) {
        for (String name : commandNames) {
            run(name, ctx, null);
        }
    }

    private void resolveRequirements(java.util.Set<Requirement> reqs, CommandContext ctx) {
        for (Requirement req : reqs) {
            switch (req) {
                case BILL_FULL_TEXT -> { /* TODO: 원문 미로드 시 파이프라인에서 로드 */ }
                case CURRENT_LAW_DIFF -> { /* TODO: baselineLawId 기준 현행법 diff 생성 */ }
                case USER_PERSONA -> {
                    if (!ctx.hasPersona()) {
                        throw new IllegalStateException("이 커맨드는 사용자 페르소나가 필요합니다.");
                    }
                }
            }
        }
    }
}
