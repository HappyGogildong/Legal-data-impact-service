package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import com.lia.core.auth.EnabledOAuthProviders;

/** client-id 가 있는 provider 만 등록(D78) — application-oauth.yml 그대로 읽는다. */
class OAuthClientRegistrationConfigTest {

    /** 시스템 환경·속성의 간섭 없이 yml + 지정 값만. */
    private static StandardEnvironment environment(Map<String, Object> values) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("oauth", new ClassPathResource("application-oauth.yml"))
                .forEach(env.getPropertySources()::addLast);
        env.getPropertySources().addFirst(new MapPropertySource("test", values));
        return env;
    }

    @Test
    @DisplayName("구글 키만 있으면 구글만 등록된다")
    void 구글만() throws IOException {
        Map<String, ClientRegistration> registrations = OAuthClientRegistrationConfig.registrations(
                environment(Map.of("GOOGLE_CLIENT_ID", "g-id", "GOOGLE_CLIENT_SECRET", "g-secret")));

        assertEquals(List.of("google"), List.copyOf(registrations.keySet()));
        assertEquals("g-id", registrations.get("google").getClientId());
    }

    @Test
    @DisplayName("세 provider 키가 모두 있으면 셋 다 — 카카오·네이버 엔드포인트도 yml 의 provider 설정대로")
    void 셋_다() throws IOException {
        Map<String, ClientRegistration> registrations = OAuthClientRegistrationConfig.registrations(environment(Map.of(
                "GOOGLE_CLIENT_ID", "g", "GOOGLE_CLIENT_SECRET", "g",
                "KAKAO_CLIENT_ID", "k", "KAKAO_CLIENT_SECRET", "k",
                "NAVER_CLIENT_ID", "n", "NAVER_CLIENT_SECRET", "n")));

        assertEquals(3, registrations.size());
        assertEquals("https://kauth.kakao.com/oauth/authorize",
                registrations.get("kakao").getProviderDetails().getAuthorizationUri());
        assertEquals("response",
                registrations.get("naver").getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
    }

    @Test
    @DisplayName("키가 하나도 없으면 등록 없음 — 예외 없이 빈 결과")
    void 없음() throws IOException {
        assertTrue(OAuthClientRegistrationConfig.registrations(environment(Map.of())).isEmpty());
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OAuthClientRegistrationConfig.class, EnabledOAuthProviders.class);

    @Test
    @DisplayName("oauth 프로파일 + 구글 키 → 저장소 빈이 생기고 켜진 목록은 [google]")
    void 컨텍스트_구글() {
        runner.withPropertyValues("spring.profiles.active=oauth",
                        "lia.oauth.registration.google.client-id=g", "lia.oauth.registration.google.client-secret=g",
                        "lia.oauth.registration.google.scope=openid,email")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(ClientRegistrationRepository.class).size());
                    assertEquals(List.of("google"), context.getBean(EnabledOAuthProviders.class).ids());
                });
    }

    @Test
    @DisplayName("oauth 프로파일이어도 키가 없으면 저장소 빈 없음 — 켜진 목록은 []")
    void 컨텍스트_없음() {
        runner.withPropertyValues("spring.profiles.active=oauth", "lia.oauth.registration.kakao.client-id=")
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(ClientRegistrationRepository.class).size());
                    assertEquals(List.of(), context.getBean(EnabledOAuthProviders.class).ids());
                });
    }

    @Test
    @DisplayName("oauth 프로파일이 아니면 키가 있어도 등록하지 않는다")
    void 프로파일_꺼짐() {
        runner.withPropertyValues("lia.oauth.registration.google.client-id=g", "lia.oauth.registration.google.client-secret=g")
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(ClientRegistrationRepository.class).size());
                    assertEquals(List.of(), context.getBean(EnabledOAuthProviders.class).ids());
                });
    }
}
