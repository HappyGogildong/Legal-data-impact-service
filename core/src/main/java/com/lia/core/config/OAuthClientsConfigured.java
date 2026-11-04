package com.lia.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** client-id 가 설정된 소셜 provider 가 하나라도 있을 때만(D78). 없으면 저장소 빈을 만들지 않는다 — 빈 저장소는 생성 자체가 실패한다. */
class OAuthClientsConfigured extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return OAuthClientRegistrationConfig.registrations(context.getEnvironment()).isEmpty()
                ? ConditionOutcome.noMatch("client-id 가 설정된 소셜 provider 없음")
                : ConditionOutcome.match("client-id 가 설정된 소셜 provider 있음");
    }
}
