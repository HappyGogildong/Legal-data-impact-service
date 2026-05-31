package com.lia.core.command.impl;

import org.springframework.stereotype.Component;

import com.lia.core.command.AnalysisCommand;
import com.lia.core.command.CommandContext;
import com.lia.core.command.Requirement;
import com.lia.core.domain.ImpactResult;

import java.util.Set;

/** 시행일 기준으로 사용자가 해야 할 일 + 기한을 제시. (참고 리포 action_plan 재해석) */
@Component
public class ActionPlanCommand implements AnalysisCommand<Void, ImpactResult> {

    @Override public String name() { return "action_plan"; }

    @Override public boolean supports(CommandContext ctx) { return true; }

    @Override public Set<Requirement> requirements() {
        return Set.of(Requirement.BILL_FULL_TEXT);
    }

    @Override public ImpactResult execute(CommandContext ctx, Void params) {
        // TODO: 시행예정일(effectiveDate)로부터 역산한 행동 타임라인 생성
        return ctx.pipeline().actionPlan(ctx.bill(), ctx.persona());
    }
}
