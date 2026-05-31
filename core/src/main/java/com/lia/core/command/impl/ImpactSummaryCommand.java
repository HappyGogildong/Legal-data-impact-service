package com.lia.core.command.impl;

import org.springframework.stereotype.Component;

import com.lia.core.command.AnalysisCommand;
import com.lia.core.command.CommandContext;
import com.lia.core.command.Requirement;
import com.lia.core.domain.ImpactResult;

import java.util.Set;

/** 법안을 평이한 말로 요약. 페르소나 없이도 동작하는 기본 커맨드. */
@Component
public class ImpactSummaryCommand implements AnalysisCommand<Void, ImpactResult> {

    @Override public String name() { return "impact_summary"; }

    @Override public boolean supports(CommandContext ctx) { return true; }

    @Override public Set<Requirement> requirements() {
        return Set.of(Requirement.BILL_FULL_TEXT);
    }

    @Override public ImpactResult execute(CommandContext ctx, Void params) {
        // 해석은 파이썬 엔진에 위임 (코어는 오케스트레이션만)
        return ctx.pipeline().summarize(ctx.bill(), null);
    }
}
