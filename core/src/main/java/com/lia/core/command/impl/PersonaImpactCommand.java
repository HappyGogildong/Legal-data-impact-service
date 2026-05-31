package com.lia.core.command.impl;

import org.springframework.stereotype.Component;

import com.lia.core.command.AnalysisCommand;
import com.lia.core.command.CommandContext;
import com.lia.core.command.Requirement;
import com.lia.core.domain.ImpactResult;

import java.util.Set;

/** 내 상황(페르소나: 직장인·자영업자·임차인·학부모…)에 무엇이 바뀌나. 개인화의 핵심. */
@Component
public class PersonaImpactCommand implements AnalysisCommand<Void, ImpactResult> {

    @Override public String name() { return "persona_impact"; }

    @Override public boolean supports(CommandContext ctx) { return ctx.hasPersona(); }

    @Override public Set<Requirement> requirements() {
        return Set.of(Requirement.BILL_FULL_TEXT, Requirement.USER_PERSONA, Requirement.CURRENT_LAW_DIFF);
    }

    @Override public ImpactResult execute(CommandContext ctx, Void params) {
        return ctx.pipeline().summarize(ctx.bill(), ctx.persona());
    }
}
