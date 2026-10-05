package com.lia.core.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.lia.core.testsupport.InMemoryAccountStore;

/**
 * OAuth2LoginSuccessHandler 단위 테스트 — subject 로 계정 회수/생성 + 세션 주입 + <b>이메일 미저장</b>(D61) +
 * 프론트 리다이렉트 + subject 누락 fail-closed. provider 분기 자체는 OAuth2IdentityTest. Docker 불필요.
 */
class OAuth2LoginSuccessHandlerTest {

    static final String FRONTEND = "http://localhost:3000";

    private static OAuth2AuthenticationToken token(String provider, Map<String, Object> attrs, String nameKey) {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, nameKey);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), provider);
    }

    private static MockHttpServletRequest login(InMemoryAccountStore store, OAuth2AuthenticationToken token,
            MockHttpServletResponse response) throws Exception {
        var request = new MockHttpServletRequest();
        new OAuth2LoginSuccessHandler(store, FRONTEND).onAuthenticationSuccess(request, response, token);
        return request;
    }

    private static UUID sessionUserId(MockHttpServletRequest request) {
        return (UUID) request.getSession().getAttribute(CurrentUser.SESSION_KEY);
    }

    @Test
    @DisplayName("최초 로그인 — (provider, subject)로 계정 생성, 세션에 userId")
    void 최초로그인() throws Exception {
        var store = new InMemoryAccountStore();
        var attrs = Map.<String, Object>of("id", 12345L, "kakao_account", Map.of("email", "user@kakao.com"));

        UUID userId = sessionUserId(login(store, token("kakao", attrs, "id"), new MockHttpServletResponse()));

        Account saved = store.findByProvider("kakao", "12345").orElseThrow();
        assertEquals(userId, saved.userId());
    }

    @Test
    @DisplayName("IdP 가 이메일을 줘도 로그인은 저장하지 않는다 — 알림 동의로만 저장(D61)")
    void 로그인_이메일미저장() throws Exception {
        var store = new InMemoryAccountStore();
        var attrs = Map.<String, Object>of("sub", "google-xyz", "email", "user@gmail.com");

        login(store, token("google", attrs, "sub"), new MockHttpServletResponse());

        Account saved = store.findByProvider("google", "google-xyz").orElseThrow();
        assertNull(saved.email());
        assertNull(saved.emailConsentedAt());
    }

    @Test
    @DisplayName("재로그인은 기존 userId 회수 — 생성 재발생 없음(멱등)")
    void 재로그인_멱등() throws Exception {
        var store = new InMemoryAccountStore();
        var attrs = Map.<String, Object>of("response", Map.of("id", "naver-abc"));

        UUID first = sessionUserId(login(store, token("naver", attrs, "response"), new MockHttpServletResponse()));
        UUID second = sessionUserId(login(store, token("naver", attrs, "response"), new MockHttpServletResponse()));

        assertEquals(first, second);
        assertEquals(1, store.creates());
    }

    @Test
    @DisplayName("성공 후 리다이렉트는 항상 프론트 URL")
    void 성공_프론트리다이렉트() throws Exception {
        var response = new MockHttpServletResponse();

        login(new InMemoryAccountStore(), token("google", Map.of("sub", "g"), "sub"), response);

        assertEquals(FRONTEND, response.getRedirectedUrl());
    }

    @Test
    @DisplayName("subject 누락 → 계정 미생성·세션 파기·프론트 로그인 오류")
    void subject누락_failClosed() throws Exception {
        var store = new InMemoryAccountStore();
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        request.setSession(session);
        var attrs = Map.<String, Object>of("response", Map.of("email", "user@naver.com"));   // id 없음

        new OAuth2LoginSuccessHandler(store, FRONTEND)
                .onAuthenticationSuccess(request, response, token("naver", attrs, "response"));

        assertEquals(0, store.creates());
        assertTrue(session.isInvalid());
        assertEquals(FRONTEND + "/login?error=identity", response.getRedirectedUrl());
    }
}
