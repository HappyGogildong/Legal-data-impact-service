package com.lia.core.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.lia.core.application.profile.ProfileUseCase;
import com.lia.core.auth.OAuth2LoginSuccessHandler;
import com.lia.core.config.SecurityConfig;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryUserProfileStore;

/**
 * 웹 슬라이스 공용 컨텍스트 — 실제 SecurityConfig + 실제 컨트롤러 + 인메모리 store.
 * HTTP 계약·인증·CSRF·예외 매핑을 한 번에 검사한다(Boot 4 는 @WebMvcTest 가 별도 모듈이라 최소 컨텍스트).
 * store 는 컨텍스트 공유 싱글턴이므로 테스트마다 새 userId 를 쓴다.
 *
 * @TestConfiguration 인 이유: 최상위 @Configuration 이면 @SpringBootTest 전체 컨텍스트의 컴포넌트 스캔에 잡혀
 * 인메모리 store 빈이 실제 @Repository 와 이름이 충돌한다.
 */
@TestConfiguration
@EnableWebMvc
@EnableWebSecurity
@Import({SecurityConfig.class, ApiExceptionHandler.class, ProfileController.class})
class ApiSliceTestConfig {

    static final String POLICY = "v-test";

    @Bean
    InMemoryUserProfileStore userProfileStore() {
        return new InMemoryUserProfileStore();
    }

    @Bean
    InMemoryAccountStore accountStore() {
        return new InMemoryAccountStore();
    }

    @Bean
    ProfileUseCase profileUseCase(InMemoryUserProfileStore store) {
        return new ProfileUseCase(store, POLICY);
    }

    @Bean
    OAuth2LoginSuccessHandler loginSuccessHandler(InMemoryAccountStore accounts) {
        return new OAuth2LoginSuccessHandler(accounts, "http://localhost:3000");
    }
}
