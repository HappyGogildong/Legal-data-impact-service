package com.lia.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lia.core.application.account.AccountUseCase;
import com.lia.core.application.profile.ProfileUseCase;
import com.lia.core.auth.AccountStore;
import com.lia.core.profile.UserProfileStore;

/**
 * 사용자(계정·프로필) 유스케이스 배선 — application 코어는 Spring 애노테이션 없이 두고 여기서 {@code @Bean} 으로 잇는다
 * ({@link PipelineConfig} 의 AnalyzeUseCase 와 같은 방식).
 */
@Configuration
public class UserConfig {

    @Bean
    public ProfileUseCase profileUseCase(UserProfileStore store,
            @Value("${lia.privacy.policy-version}") String policyVersion) {
        return new ProfileUseCase(store, policyVersion);
    }

    @Bean
    public AccountUseCase accountUseCase(AccountStore store) {
        return new AccountUseCase(store);
    }
}
