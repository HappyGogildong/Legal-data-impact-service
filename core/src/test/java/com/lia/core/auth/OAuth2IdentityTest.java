package com.lia.core.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/** OAuth2Identity — provider 3종 (subject, email) 추출. 로그인 핸들러·알림 동의가 공유하는 유일한 분기. */
class OAuth2IdentityTest {

    private static OAuth2AuthenticationToken token(String provider, Map<String, Object> attrs, String nameKey) {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, nameKey);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), provider);
    }

    @Test
    @DisplayName("google — top-level sub·email")
    void google() {
        var id = OAuth2Identity.of(token("google", Map.of("sub", "g-1", "email", "a@gmail.com"), "sub"));
        assertEquals(new OAuth2Identity("g-1", "a@gmail.com"), id);
    }

    @Test
    @DisplayName("kakao — top-level id(Long→String) + kakao_account.email 중첩")
    void kakao() {
        var attrs = Map.<String, Object>of("id", 12345L, "kakao_account", Map.of("email", "a@kakao.com"));
        assertEquals(new OAuth2Identity("12345", "a@kakao.com"), OAuth2Identity.of(token("kakao", attrs, "id")));
    }

    @Test
    @DisplayName("naver — response.id·response.email 중첩")
    void naver() {
        var attrs = Map.<String, Object>of("response", Map.of("id", "n-1", "email", "a@naver.com"));
        assertEquals(new OAuth2Identity("n-1", "a@naver.com"), OAuth2Identity.of(token("naver", attrs, "response")));
    }

    @Test
    @DisplayName("중첩 속성이 없으면 null — email 미동의, naver id 누락")
    void 누락은_null() {
        var kakao = OAuth2Identity.of(token("kakao", Map.of("id", 1L), "id"));
        assertNull(kakao.email());
        assertTrue(kakao.hasSubject());

        var naver = OAuth2Identity.of(token("naver", Map.of("response", Map.of("email", "a@naver.com")), "response"));
        assertNull(naver.subject());
        assertFalse(naver.hasSubject());
    }

    @Test
    @DisplayName("미지원 provider → IllegalStateException(설정 실수 fail-fast)")
    void 미지원provider() {
        assertThrows(IllegalStateException.class,
                () -> OAuth2Identity.of(token("github", Map.of("id", 1), "id")));
    }
}
