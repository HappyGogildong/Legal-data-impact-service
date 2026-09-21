package com.lia.core.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * OAuth2LoginSuccessHandler 단위 테스트 — provider별 속성 추출 + 계정 회수/생성 + 세션 주입.
 * Fake AccountStore(인메모리), 실제 principal/토큰, Mock 요청·응답. Docker 불필요.
 */
class OAuth2LoginSuccessHandlerTest {

    /** 인메모리 Fake — JdbcClient 없이 findByProvider·create 만 대체. */
    static class FakeAccountStore extends AccountStore {
        final Map<String, Account> byIdentity = new HashMap<>();
        int creates = 0;

        FakeAccountStore() { super(null); }

        @Override
        public Optional<Account> findByProvider(String provider, String providerId) {
            return Optional.ofNullable(byIdentity.get(provider + "|" + providerId));
        }

        @Override
        public Account create(String provider, String providerId, String email) {
            creates++;
            var account = new Account(UUID.randomUUID(), provider, providerId, email,
                    OffsetDateTime.now(ZoneOffset.UTC));
            byIdentity.put(provider + "|" + providerId, account);
            return account;
        }
    }

    private static OAuth2AuthenticationToken token(String provider, Map<String, Object> attrs, String nameKey) {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, nameKey);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), provider);
    }

    private UUID login(FakeAccountStore store, OAuth2AuthenticationToken token) throws Exception {
        var handler = new OAuth2LoginSuccessHandler(store);
        var request = new MockHttpServletRequest();
        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(), token);
        return (UUID) request.getSession().getAttribute(CurrentUser.SESSION_KEY);
    }

    @Test
    @DisplayName("kakao — top-level id + kakao_account.email 중첩 추출, 최초 로그인 create")
    void kakao_최초로그인() throws Exception {
        var store = new FakeAccountStore();
        var attrs = Map.<String, Object>of(
                "id", 12345L,
                "kakao_account", Map.of("email", "user@kakao.com"));

        UUID userId = login(store, token("kakao", attrs, "id"));

        assertNotNull(userId, "세션에 userId 주입");
        Account saved = store.findByProvider("kakao", "12345").orElseThrow();
        assertEquals(userId, saved.userId());
        assertEquals("user@kakao.com", saved.email(), "중첩 email 추출");
    }

    @Test
    @DisplayName("naver — response 중첩에서 id·email 추출")
    void naver_중첩추출() throws Exception {
        var store = new FakeAccountStore();
        var attrs = Map.<String, Object>of(
                "response", Map.of("id", "naver-abc", "email", "user@naver.com"));

        UUID userId = login(store, token("naver", attrs, "response"));

        Account saved = store.findByProvider("naver", "naver-abc").orElseThrow();
        assertEquals(userId, saved.userId());
        assertEquals("user@naver.com", saved.email());
    }

    @Test
    @DisplayName("google — top-level sub·email 추출")
    void google_topLevel() throws Exception {
        var store = new FakeAccountStore();
        var attrs = Map.<String, Object>of("sub", "google-xyz", "email", "user@gmail.com");

        login(store, token("google", attrs, "sub"));

        Account saved = store.findByProvider("google", "google-xyz").orElseThrow();
        assertEquals("user@gmail.com", saved.email());
    }

    @Test
    @DisplayName("재로그인은 기존 userId 회수 — create 재호출 없음(멱등)")
    void 재로그인_멱등() throws Exception {
        var store = new FakeAccountStore();
        var attrs = Map.<String, Object>of("sub", "google-xyz", "email", "user@gmail.com");

        UUID first = login(store, token("google", attrs, "sub"));
        UUID second = login(store, token("google", attrs, "sub"));

        assertEquals(first, second, "같은 신원 → 같은 userId");
        assertEquals(1, store.creates, "두 번째는 회수만, create 안 함");
    }

    @Test
    @DisplayName("email 미동의(속성 없음) → null 로 저장(알림 채널 nullable)")
    void email_미동의() throws Exception {
        var store = new FakeAccountStore();
        var attrs = Map.<String, Object>of("id", 999L);   // kakao_account 없음

        login(store, token("kakao", attrs, "id"));

        assertNull(store.findByProvider("kakao", "999").orElseThrow().email());
    }
}
