package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** application-oauth.yml 의 리다이렉트 주소 — 환경변수가 없으면 요청 기준, 있으면 프런트 도메인(D64). */
class OAuthRedirectUriConfigTest {

    static final String KEY = "spring.security.oauth2.client.registration.%s.redirect-uri";

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
    @DisplayName("환경변수가 없으면 {baseUrl} — 요청 기준(로컬 개발 그대로)")
    void 기본값() throws IOException {
        StandardEnvironment env = environment(Map.of());
        for (String id : new String[] {"google", "kakao", "naver"}) {
            assertEquals("{baseUrl}/login/oauth2/code/" + id, env.getProperty(KEY.formatted(id)), id);
        }
    }

    @Test
    @DisplayName("OAUTH2_REDIRECT_BASE_URL 이 있으면 그 도메인 — Vercel rewrites 로 콜백이 백엔드에 온다")
    void 프런트도메인() throws IOException {
        StandardEnvironment env = environment(Map.of("OAUTH2_REDIRECT_BASE_URL", "https://app.example.com"));
        for (String id : new String[] {"google", "kakao", "naver"}) {
            assertEquals("https://app.example.com/login/oauth2/code/" + id, env.getProperty(KEY.formatted(id)), id);
        }
    }
}
